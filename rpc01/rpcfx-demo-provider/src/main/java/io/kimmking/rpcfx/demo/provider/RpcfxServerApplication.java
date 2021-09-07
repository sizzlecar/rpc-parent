package io.kimmking.rpcfx.demo.provider;

import com.alibaba.fastjson.JSON;
import io.kimmking.rpcfx.annotation.RpcService;
import io.kimmking.rpcfx.api.RpcfxRequest;
import io.kimmking.rpcfx.api.RpcfxResolver;
import io.kimmking.rpcfx.api.RpcfxResponse;
import io.kimmking.rpcfx.api.ServiceProviderDesc;
import io.kimmking.rpcfx.server.RpcfxInvoker;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.PostConstruct;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
@RestController
@Slf4j
public class RpcfxServerApplication implements ApplicationContextAware {

	private ApplicationContext applicationContext;


	@Value("${server.port}")
	private Integer port;


	/**
	 * spring启动之后，扫描所有加了 @RpcService 注解service，注册到zookeeper中
	 */
	@PostConstruct
	public void registerService(){
		// start zk client
		RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
		CuratorFramework client = CuratorFrameworkFactory.builder().connectString("localhost:2181").namespace("rpcfx").retryPolicy(retryPolicy).build();
		client.start();
		Map<String, Object> rpcServiceBeanMap = this.applicationContext.getBeansWithAnnotation(RpcService.class);
		rpcServiceBeanMap.forEach((k, v) -> {
			try{
				registerService(client, v.getClass().getInterfaces()[0].getName(), port);
			}catch (Exception e){
				log.error("registerService happen error", e);
			}
		});
	}


	public static void main(String[] args) throws Exception {

		// start zk client
		/*RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
		CuratorFramework client = CuratorFrameworkFactory.builder().connectString("localhost:2181").namespace("rpcfx").retryPolicy(retryPolicy).build();
		client.start();*/


		// register service
		// xxx "io.kimmking.rpcfx.demo.api.UserService"

		/*String userService = "io.kimking.rpcfx.demo.api.UserService";
		registerService(client, userService);
		String orderService = "io.kimking.rpcfx.demo.api.OrderService";
		registerService(client, orderService);*/


		// 进一步的优化，是在spring加载完成后，从里面拿到特定注解的bean，自动注册到zk done

		SpringApplication.run(RpcfxServerApplication.class, args);
	}

	private static void registerService(CuratorFramework client, String service, Integer port) throws Exception {
		ServiceProviderDesc userServiceSesc = ServiceProviderDesc.builder()
				.host(InetAddress.getLocalHost().getHostAddress())
				.port(port).serviceClass(service).build();
		// String userServiceSescJson = JSON.toJSONString(userServiceSesc);

		try {
			if ( null == client.checkExists().forPath("/" + service)) {
				Map<String, String> map = new HashMap<>(1);
				map.put("type", "service");
				client.create().withMode(CreateMode.PERSISTENT).forPath("/" + service, JSON.toJSONBytes(map));
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		client.create().withMode(CreateMode.EPHEMERAL).
				forPath( "/" + service + "/" + userServiceSesc.getHost() + "_" + userServiceSesc.getPort(), JSON.toJSONBytes(userServiceSesc));
	}

	@Autowired
	RpcfxInvoker invoker;

	@PostMapping("/")
	public RpcfxResponse invoke(@RequestBody RpcfxRequest request) {
		return invoker.invoke(request);
	}

	@Bean
	public RpcfxInvoker createInvoker(@Autowired RpcfxResolver resolver){
		return new RpcfxInvoker(resolver);
	}

	@Bean
	public RpcfxResolver createResolver(){
		return new DemoResolver();
	}

	// 能否去掉name
	//

	// annotation


	/*@Bean(name = "io.kimmking.rpcfx.demo.api.UserService")
	public UserService createUserService(){
		return new UserServiceImpl();
	}

	@Bean(name = "io.kimmking.rpcfx.demo.api.OrderService")
	public OrderService createOrderService(){
		return new OrderServiceImpl();
	}*/



	@Component
	public class AddResponseHeaderFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
										FilterChain filterChain) throws ServletException, IOException {
			String requestId = httpServletRequest.getHeader("request_id");
			if(!StringUtils.isEmpty(requestId)){
				httpServletResponse.addHeader("request_id", requestId);
			}
			filterChain.doFilter(httpServletRequest, httpServletResponse);

		}
	}


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
