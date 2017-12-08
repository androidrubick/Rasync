package androidrubick.android.async;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import androidrubick.base.utils.Exceptions;
import androidrubick.base.utils.Primitives;

/**
 *
 * <pre>
 *     interface A {
 *         void a();
 *     }
 *
 *     A a = new AImpl(...);
 *     A asyncA = AsyncProxy.async(a);
 *
 *     // we make sure that methods of A are invoked in main ui thread
 *     asyncA.a();
 * </pre>
 *
 * <p></p>
 * Created by Yin Yong on 2017/12/1.
 *
 * @since 1.0.0
 */
public class AsyncProxy {
    /**
     * @param raw raw object, holding interface(s)
     * @param <TS> super interface(s) to async proxy
     * @param <T> class type of raw object
     * @since 1.0.0
     */
    public static <TS, T extends TS> TS async(T raw) {
        if (null == raw) {
            return raw;
        }
        Class[] superInterfaces = raw.getClass().getInterfaces();
        return (TS) Proxy.newProxyInstance(raw.getClass().getClassLoader(), superInterfaces, new AsyncProxyWrapper(raw));
    }

    private static class AsyncProxyWrapper implements Handler.Callback, InvocationHandler {
        private final Handler mHandler = new Handler(Looper.getMainLooper(), this);
        private final Object mRaw;

        AsyncProxyWrapper(Object raw) {
            mRaw = raw;
        }

        @Override
        public boolean handleMessage(Message msg) {
            try {
                Method method;
                Object args[] = null;
                if (msg.obj instanceof Method) {
                    method = (Method) msg.obj;
                } else {
                    Map<String, Object> data = (Map<String, Object>) msg.obj;
                    method = (Method) data.get("method");
                    args = (Object[]) data.get("args");
                }
                method.invoke(mRaw, args);
            } catch (Throwable e) {
                throw Exceptions.asRuntime(e);
            }
            return true;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                return method.invoke(mRaw, args);
            } else {
                Message msg = mHandler.obtainMessage(0);
                if (null != args && args.length > 0) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("method", method);
                    data.put("args", args);
                    msg.obj = data;
                } else {
                    msg.obj = method;
                }
                msg.sendToTarget();
                return Primitives.defValueOf(method.getReturnType());
            }
        }
    }

}
