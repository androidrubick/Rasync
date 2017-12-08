package pub.androidrubick.demo;

import androidrubick.async.annotations.Async;

/**
 * {@doc}
 * <p>
 * Created by Yin Yong on 2017/11/30.
 */
public class TestNestedCases {

    private static class TestAsyncEnclosingClass {
        // 已验证
//        @Async
//        interface A {
//
//        }
    }

    @Async
    interface TestAsyncEnclosingIF {
        @Async
        interface A {

        }
    }

}
