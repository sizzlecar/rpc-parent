package io.kimmking.rpcfx.client;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.ParserConfig;
import io.kimmking.rpcfx.api.*;
import io.kimmking.rpcfx.exception.RpcfxException;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.AsciiString;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpMethod.POST;

@Slf4j
public final class Rpcfx {

    private static final CuratorFramework CURATOR_FRAMEWORK;

    static {
        ParserConfig.getGlobalInstance().addAccept("io.kimmking");
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CURATOR_FRAMEWORK = CuratorFrameworkFactory.builder().connectString("localhost:2181").namespace("rpcfx").retryPolicy(retryPolicy).build();
        CURATOR_FRAMEWORK.start();

    }

    public static <T, filters> T createFromRegistry(final Class<T> serviceClass, final String zkUrl, Router router, LoadBalancer loadBalance, Filter filter) {

        // ???filte??????

        // curator Provider list from zk
        ServiceDiscoveryBuilder<String> discoveryBuilder = ServiceDiscoveryBuilder
                .builder(String.class)
                .client(CURATOR_FRAMEWORK)
                .watchInstances(true)
                .basePath("/" + serviceClass.getName());
        ServiceDiscovery<String> stringServiceDiscovery = discoveryBuilder.build();
        try {
            stringServiceDiscovery.start();
        } catch (Exception e) {
            log.error("ServiceDiscovery start error");
        }

        Collection<String> instances = null;
        try{
            instances = stringServiceDiscovery.queryForNames();
        }catch (Exception e){
            log.error("????????????????????????", e);
            throw new RuntimeException("????????????????????????");
        }

        List<String> invokerList = instances
                .stream()
                .map(str -> {
                    String[] strings = str.split("_");
                    return  "http://" + strings[0] + ":" + strings[1] + "/";
                }).collect(Collectors.toList());

        // 1. ????????????zk???????????????????????????
        // 2. ???????????????zk??????????????????????????????????????????list??????????????????????????????map??????????????????????????????List???

        List<String> urls = router.route(invokerList);

        String url = loadBalance.select(urls); // router, loadbalance

        return (T) create(serviceClass, url, filter);

    }

    public static <T> T create(final Class<T> serviceClass, final String url, Filter... filters) {

        // 0. ?????????????????? -> ???????????????
        //return (T) Proxy.newProxyInstance(Rpcfx.class.getClassLoader(), new Class[]{serviceClass}, new RpcfxInvocationHandler(serviceClass, url, filters));
        return (T) new JavassistInvocationHandler(serviceClass, url, filters).getProxy(serviceClass);
    }

    public static class RpcfxInvocationHandler implements InvocationHandler {

        public static final MediaType JSONTYPE = MediaType.get("application/json; charset=utf-8");

        private final Class<?> serviceClass;
        private final String url;
        private final Filter[] filters;

        public <T> RpcfxInvocationHandler(Class<T> serviceClass, String url, Filter... filters) {
            this.serviceClass = serviceClass;
            this.url = url;
            this.filters = filters;
        }

        // ??????????????????????????????????????????????????????????????????????????????rpcfx???xml????????????????????????????????????json: code.google.com/p/rpcfx
        // int byte char float double long bool
        // [], data class

        @Override
        public Object invoke(Object proxy, Method method, Object[] params) throws Throwable {

            // ???filter????????????
            // mock == true, new Student("hubao");

            RpcfxRequest request = new RpcfxRequest();
            request.setServiceClass("io.kimmking.rpcfx.demo.provider");
            request.setMethod(method.getName());
            request.setParams(params);

            if (null!=filters) {
                for (Filter filter : filters) {
                    if (!filter.filter(request)) {
                        return null;
                    }
                }
            }

            RpcfxResponse response = post(request, url);

            // ???filter????????????
            // Student.setTeacher("cuijing");

            // ????????????response.status???????????????
            // ???????????????????????????RpcfxException

            return JSON.parse(response.getResult().toString());
        }

        private RpcfxResponse post(RpcfxRequest req, String url) throws IOException {
            String reqJson = JSON.toJSONString(req);
            System.out.println("req json: "+reqJson);

            // 1.????????????client
            // 2.????????????httpclient??????netty client
            OkHttpClient client = new OkHttpClient();
            final Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(JSONTYPE, reqJson))
                    .build();
            String respJson = client.newCall(request).execute().body().string();
            System.out.println("resp json: "+respJson);
            return JSON.parseObject(respJson, RpcfxResponse.class);
        }
    }

    public static class JavassistInvocationHandler implements MethodHandler{

        public static final MediaType JSONTYPE = MediaType.get("application/json; charset=utf-8");

        private final Class<?> serviceClass;
        private final String url;
        private final Filter[] filters;

        public <T> JavassistInvocationHandler(Class<T> serviceClass, String url, Filter... filters) {
            this.serviceClass = serviceClass;
            this.url = url;
            this.filters = filters;
        }


        private <T> T getProxy(Class<T> clazz)  {
            javassist.util.proxy.ProxyFactory proxyFactory = new javassist.util.proxy.ProxyFactory();
            proxyFactory.setInterfaces(new Class[]{clazz});
            Class<?> proxyClass = proxyFactory.createClass();
            T result = null;
            try {
                result = (T)proxyClass.newInstance();
            }catch (InstantiationException | IllegalAccessException e){
                e.printStackTrace();
                return null;
            }
            ((ProxyObject)result).setHandler(this);
            return result;
        }

        @Override
        public Object invoke(Object obj, Method thisMethod, Method method, Object[] args) throws Throwable {
            String thisMethodName = thisMethod.getName();
            System.out.println("thisMethod:" + thisMethodName);
            System.out.println("Method:" + (method == null ? "" : method.getName()));

            if (thisMethod.getDeclaringClass() == Object.class) {
                if (thisMethodName.equals("toString"))
                    return "Proxy[" + toString() + "]";
                else if (thisMethodName.equals("hashCode") && args == null)
                    return hashCode() + 0x43444948;
                else if (thisMethodName.equals("equals") && args.length == 1
                        && method.getParameterTypes()[0] == Object.class)
                    return equals(args[0]);
                else {
                /* Either someone is calling invoke by hand, or
                   it is a non-final method from Object overriden
                   by the generated Proxy.  At the time of writing,
                   the only non-final methods in Object that are not
                   handled above are finalize and clone, and these
                   are not overridden in generated proxies.  */
                    // this plain Method.invoke is called only if the declaring class
                    // is Object and so it's safe.
                    return method.invoke(this, args);
                }
            }

            RpcfxRequest request = new RpcfxRequest();
            request.setServiceClass(this.serviceClass.getName());
            request.setMethod(thisMethodName);
            request.setParams(args);

            if (null!=filters) {
                for (Filter filter : filters) {
                    if (!filter.filter(request)) {
                        return null;
                    }
                }
            }

            RpcfxResponse response = null;
            try {
                response = post(request, url);
            }catch (Exception e){
                e.printStackTrace();
                System.out.println("????????????????????????" +  e.getMessage());
                throw new RpcfxException(e, "E001", "????????????????????????");
            }

            // ???filter????????????
            // Student.setTeacher("cuijing");

            // ????????????response.status???????????????
            // ???????????????????????????RpcfxException
            if(!response.isStatus()){
                throw new RpcfxException(response.getException(), "E001", "????????????????????????");
            }

            return JSON.parse(response.getResult().toString());
        }

        private RpcfxResponse post(RpcfxRequest req, String url) {
            // 1.????????????client
            // 2.????????????httpclient??????netty client
            url = url.replace("http://", "");
            String[] urlArr = url.split(":");
            if(urlArr.length != 2){
                throw new RpcfxException("E002", "?????????url??????");
            }

            String host = urlArr[0];
            String port = urlArr[1].replace("/","");
            EventLoopGroup workerGroup = new NioEventLoopGroup();
            Http2ClientInitializer initializer = new Http2ClientInitializer(null, Integer.MAX_VALUE);
            HttpResponseHandler responseHandler = null;
            String uuid = UUID.randomUUID().toString();
            try {
                // Configure the client.
                Bootstrap b = new Bootstrap();
                b.group(workerGroup);
                b.channel(NioSocketChannel.class);
                b.option(ChannelOption.SO_KEEPALIVE, true);
                b.remoteAddress(host, Integer.parseInt(port));
                b.handler(initializer);

                // Start the client.
                Channel channel = b.connect().syncUninterruptibly().channel();
                System.out.println("Connected to [" + host + ':' + port + ']');

                // Wait for the HTTP/2 upgrade to occur.
                //Http2SettingsHandler http2SettingsHandler = initializer.settingsHandler();
                //http2SettingsHandler.awaitSettings(5, TimeUnit.SECONDS);
                responseHandler = initializer.responseHandler();
                HttpScheme scheme = HttpScheme.HTTP;
                AsciiString hostName = new AsciiString(host + ':' + port);
                System.err.println("Sending request(s)...");
                String jsonString = JSONObject.toJSONString(req);
                ByteBuf content = Unpooled.copiedBuffer(jsonString, StandardCharsets.UTF_8);
                FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, POST, "/", content);
                httpRequest.headers().add(HttpHeaderNames.HOST, hostName);
                httpRequest.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme.name());
                httpRequest.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
                httpRequest.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.DEFLATE);
                httpRequest.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
                httpRequest.headers().add(HttpHeaderNames.CONTENT_LENGTH, jsonString.getBytes().length);
                httpRequest.headers().add("request_id", uuid);
                responseHandler.put(uuid, channel.write(httpRequest), channel.newPromise());
                channel.flush();
                responseHandler.awaitResponses(5, TimeUnit.SECONDS);
                RpcfxResponse response = responseHandler.getResponse(uuid);
                System.out.println("Finished HTTP/2 request(s)");
                System.out.println(JSONObject.toJSONString(response));
                // Wait until the connection is closed.
                channel.close().syncUninterruptibly();
            } finally {
                workerGroup.shutdownGracefully();
            }
            return responseHandler.getResponse(uuid);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JavassistInvocationHandler that = (JavassistInvocationHandler) o;
            return Objects.equals(serviceClass, that.serviceClass) && Objects.equals(url, that.url) && Arrays.equals(filters, that.filters);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(serviceClass, url);
            result = 31 * result + Arrays.hashCode(filters);
            return result;
        }

        @Override
        public String toString() {
            return "JavassistInvocationHandler{" +
                    "serviceClass=" + serviceClass +
                    ", url='" + url + '\'' +
                    ", filters=" + Arrays.toString(filters) +
                    '}';
        }
    }

}
