package androidrubick.test;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * {@doc}
 * <p>
 * Created by Yin Yong on 2017/12/4.
 */
public class TestProxy {

    interface A {
        void a(int a, String b);
    }

    interface B extends A {
        <T>void b(Map<T, ?> map, T key);
    }

    interface C<T> {
        T get() ;

        void put(T t) ;
    }

    interface D<T extends CharSequence> extends C<T> {

    }

    @Test
    public void d() {
        D a = proxy(new D<String>() {
            @Override
            public String get() {
                return null;
            }

            @Override
            public void put(String o) {

            }
        });

//        a.a(0, "1");
//        a.b(new HashMap<String, Object>(), "");
        a.put("");
        a.get();
    }

    private static <TS, T extends TS> TS proxy(final T t) {
        Class<?> tClz = t.getClass();
        return (TS) Proxy.newProxyInstance(tClz.getClassLoader(), tClz.getInterfaces(),
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        return method.invoke(t, args);
                    }
                });
    }

}
