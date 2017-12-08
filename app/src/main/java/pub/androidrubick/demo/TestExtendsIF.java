package pub.androidrubick.demo;

import java.util.Map;

import androidrubick.async.annotations.Async;

/**
 * {@doc}
 * <p>
 * Created by Yin Yong on 2017/11/30.
 */
public class TestExtendsIF {

    // 单参数方法，已验证
    @Async
    interface A {
        void a(int a);
    }

    // 多参数方法，已验证
    @Async
    interface B {
        void b(int a, String b, float c, boolean d);
    }

    // 继承方法，已验证
    @Async
    interface AB extends A, B {
    }

    // 包含泛型变量的参数
    @Async
    interface C extends A, B {
        <T>void c(Map<T, ?> map, T key) ;
    }

    @Async
    interface D<T1, T2 extends CharSequence> {
        void a();
        void b(T1 t);
        void c(T2 t);
        <T extends T1>void d(T1 t1, T t);
    }

    @Async
    interface E<T> extends D<T, String> {
        @Override
        void c(String t);

        @Override
        <T1 extends T> void d(T t, T1 t1);
    }

    @Async
    interface F<T> extends E<T> {
    }
}
