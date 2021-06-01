package io.kimmking.rpcfx.client;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;

import java.lang.reflect.Method;

public class JavassitDemo implements MethodHandler {

    public static void main(String[] args) throws IllegalAccessException, InstantiationException {
        User user = new JavassitDemo().getProxy(User.class);
        user.setId(1);
        System.out.println("Id: "+user.getId());
    }
    private <T> T getProxy(Class<T> clazz) throws InstantiationException, IllegalAccessException {
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setSuperclass(clazz);
        Class<?> proxyClass = proxyFactory.createClass();
        T result = (T)proxyClass.newInstance();
        ((ProxyObject)result).setHandler(this);
        return result;
    }

    @Override
    public Object invoke(Object obj, Method thisMethod, Method method, Object[] args) throws Throwable {
        System.out.println("执行开始：" + method);
        Object result = method.invoke(obj, args);
        System.out.println("执行结束：" + method);
        return result;
    }
    public static class User {
        private int id;
        public int getId() {return id;}
        public void setId(int id) {this.id = id;}
    }
}
