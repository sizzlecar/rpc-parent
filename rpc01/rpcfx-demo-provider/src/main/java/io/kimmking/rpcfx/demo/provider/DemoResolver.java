package io.kimmking.rpcfx.demo.provider;

import io.kimmking.rpcfx.api.RpcfxResolver;
import org.reflections.Reflections;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import sun.reflect.Reflection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DemoResolver implements RpcfxResolver {

    private final Map<String, Class> NAME_CLASS_CACHE = new ConcurrentHashMap<>();

    @Override
    public <T> T resolve(String serviceClass) {

        Class cacheClazz = NAME_CLASS_CACHE.get(serviceClass);
        if(cacheClazz != null){
            try {
                return (T) cacheClazz.newInstance();
            }catch (Exception e){
                e.printStackTrace();
                return null;
            }
        }

        Class clazz = null;
        try {
            clazz = Class.forName(serviceClass);
        }catch (ClassNotFoundException e){
            e.printStackTrace();
            return null;
        }

        if(clazz.isInterface()){
            Reflections reflections = new Reflections("io.kimmking");
            Set<Class> subTypes = reflections.getSubTypesOf(clazz);
            List<Class> clazzList = new ArrayList<>(subTypes);
            if(clazzList.size() != 0){
                Class aClass = clazzList.get(0);
                NAME_CLASS_CACHE.put(serviceClass, aClass);
                try {
                    return (T) aClass.newInstance();
                } catch (IllegalAccessException | InstantiationException e) {
                    e.printStackTrace();
                }
                return null;
            }
        }

        Class c = null;
        try{
            c = Class.forName(serviceClass);
        }catch (ClassNotFoundException e){
            e.printStackTrace();
            return null;
        }

        try {
            NAME_CLASS_CACHE.put(serviceClass, c);
            return (T) c.newInstance();
        } catch (IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        }

        return null;

    }

}
