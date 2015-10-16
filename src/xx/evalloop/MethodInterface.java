/**
 * Copyright (C) 2013 - 2015 Oracle and/or its affiliates. All rights reserved.
 */
package xx.evalloop;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public interface MethodInterface {

    Object invoke(Object... pars);


    static MethodInterface forStaticMethod(Method staticMethod) {
        return new MethodInterface() {

            @Override
            public String toString() {
                return "Invoker for: " + staticMethod.toString();
            }

            @Override
            public Object invoke(Object... pars) {
                try {
                    return staticMethod.invoke(null, pars);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new UnsupportedOperationException(e);
                }
            }
        };

    }
}
