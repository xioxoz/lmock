/* **************************************************************************
 * Copyright (C) 2010-2011 VMware, Inc. All rights reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0.
 * Please see the LICENSE file to review the full text of the Apache License 2.0.
 * You may not use this product except in compliance with the License.
 * ************************************************************************** */
package com.vmware.lmock.impl;

import static com.vmware.lmock.impl.MockInvocationHandlerType.CHECKER;
import static com.vmware.lmock.impl.MockInvocationHandlerType.CONSTRUCTOR;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import com.vmware.lmock.exception.LMRuntimeException;
import com.vmware.lmock.exception.MockCreationException;
import com.vmware.lmock.exception.MockReferenceException;
import com.vmware.lmock.exception.UnexpectedInvocationError;

/**
 * Objects that mock interfaces.
 *
 * <p>
 * Such a mock is generated by the static method <code>getObject</code>.
 * </p>
 */
public class Mock implements InvocationHandler {

    /** Logs the mock activity. */
    private static final Logger logger = Logger.get(Mock.class);
    /** Mock counter, used to assign unique identifiers. */
    private static long uidCount = 0L;
    /** Unique identifier of this. */
    private final long uid;
    /** The mocked class. */
    private final Class<?> clazz;
    /** The proxy object. */
    private final Object proxy;
    /** The current invocation handlers associated to the mock. One per type. */
    private final MockInvocationHandler[] handlers =
      new MockInvocationHandler[MockInvocationHandlerType.values().length];
    /** Invocation hooks providing default methods when the mock has no handler. */
    private final InvocationHooks defaultHooks = new InvocationHooks();
    /** Name of this mock. */
    private final String name;

    /**
     * Generates a new object mocking a user supplied class.
     *
     * @param <T>
     *            the type of mock object
     * @param clazz
     *            class defining the type of mock object
     * @return The proxy object.
     * @throws MockCreationException
     *             The mock object cannot be created.
     */
    public static <T> T getObject(Class<T> clazz) throws MockCreationException {
        return getObject(null, clazz);
    }

    /**
     * Generates a new object mocking a user supplied class.
     *
     * <p>
     * The mock is given a specific name
     * </p>
     *
     * @param <T>
     *            the type of mock object
     * @param name
     *            the mock name
     * @param clazz
     *            class defining the type of mock object
     * @return The proxy object.
     * @throws MockCreationException
     *             The mock object cannot be created.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getObject(String name, Class<T> clazz) throws MockCreationException {
        logger.trace("getObject", null, "name =", name, "class =", clazz);

        try {
            return (T) new Mock(name, clazz).getProxy();
        } catch (Exception e) {
            throw new MockCreationException(e);
        }
    }

    /**
     * Gets the proxy object that THEORETICALLY wraps a mock object.
     *
     * @param <T>
     *            the type of the requested object
     * @param object
     *            the requested object
     * @return The corresponding proxy object, always valid.
     * @throws MockReferenceException
     *             The specified object is not a mock.
     */
    protected static <T> Mock getProxyOrThrow(T object) throws MockReferenceException {
        logger.trace("getProxyOrThrow", null, "object=", object);

        try {
            // Check that we have an invocation handler and that it's a proxy.
            InvocationHandler handler = Proxy.getInvocationHandler(object);
            if (handler instanceof Mock) {
                return (Mock) handler;
            } else {
                throw new MockReferenceException(
                  "referencing a non-mock object");
            }
        } catch (Exception e) {
            throw new MockReferenceException("referencing a non-mock object", e);
        }
    }

    /**
     * Checks if an object is a mock and returns the mock if found.
     *
     * @param object
     *            the requested object
     * @return The object, or the mock.
     */
    protected static Object getObjectOrMock(Object object) {
        if (object == null) {
            return object;
        }

        try {
            // Check that we have an invocation handler and that it's a proxy.
            InvocationHandler handler = Proxy.getInvocationHandler(object);
            if (handler instanceof Mock) {
                return handler;
            } else {
                return object;
            }
        } catch (Exception e) {
            return object;
        }
    }

    /**
     * @return The default name of a mock, if unspecified.
     */
    private String defaultMockName() {
        return "Mock(" + clazz.getSimpleName() + ")$" + uid;
    }

    /**
     * Creates a new mock object.
     *
     * @param name
     *            the name of this mock, <code>null</code> to let the name be
     *            automatically assigned
     * @param clazz
     *            type of the mock object
     */
    private Mock(String name, Class<?> clazz) {
        uid = uidCount++;
        this.clazz = clazz;
        this.name = (name == null) ? defaultMockName() : name;
        proxy = Proxy.newProxyInstance(clazz.getClassLoader(),
          new Class<?>[]{clazz}, this);

        logger.trace("Mock", name, "new mock: uid=", uid, "class=", clazz, "proxy=", proxy);
    }

    /** @return The unique identifier of this object. */
    protected long getUid() {
        return uid;
    }

    /** @return The associated proxy object. */
    protected Object getProxy() {
        return proxy;
    }

    /** @return The class of the mock object. */
    protected Class<?> getMockedClass() {
        return clazz;
    }

    /**
     * Assigns an invocation handler, called when a method of the mock object is
     * invoked.
     *
     * <p>
     * Avoids to push the same handler twice.
     * </p>
     *
     * @param type
     *            the invocation handler type
     * @param handler
     *            the assigned handler
     */
    protected void setInvocationHandler(MockInvocationHandlerType type,
      MockInvocationHandler handler) {
        logger.trace("setInvocationHandler", name, "type=", type, " handler=", handler);

        int index = type.ordinal();
        if (type == CONSTRUCTOR && handlers[index] != null) {
            // Pushing a constructor above a constructor is a bug.
            throw new LMRuntimeException("BUG: constructing twice!");
        }

        handlers[index] = handler;
        Cleaner.register(this);
    }

    /**
     * Removes the association of this mock to a handler.
     *
     * @param type
     *            the invocation handler type
     */
    protected void unsetInvocationHandler(MockInvocationHandlerType type) {
        logger.trace("unsetInvocationHandler", name, "type=", type);

        handlers[type.ordinal()] = null;
    }

    /** Removes all the current invocation handlers. */
    protected void cleanupInvocationHandlers() {
        logger.trace("cleanupInvocationHandlers", name);

        for (int index = 0; index < handlers.length; index++) {
            handlers[index] = null;
        }
    }

    /**
     * Selects an invocation handler if any.
     *
     * <p>
     * Puts the priority on the constructor.
     * </p>
     *
     * @return The fetched handler, null if none.
     */
    private MockInvocationHandler selectInvocationHandler() {
        if (handlers[CONSTRUCTOR.ordinal()] != null) {
            logger.trace("selectInvocationHandler", name, "select CONSTRUCTOR");
            return handlers[CONSTRUCTOR.ordinal()];
        } else {
            logger.trace("selectInvocationHandler", name, "select CHECKER");
            return handlers[CHECKER.ordinal()];
        }
    }

    /**
     * Tries to invoke a default handler for an invocation if this mock has no
     * invocation handler.
     *
     * @param invocation
     *            the invocation
     * @return The non null invocation result if a default handler exists.
     * @throws UnexpectedInvocationException
     *             The specified invocation cannot be handled.
     */
    private InvocationResult tryDefaultInvocation(Invocation invocation) {
        InvocationResult result = defaultHooks.tryInvocation(invocation);
        if (result == null) {
            // In that specific "out of band" case, we should not forget that
            // a test may be ongoing and so the thrown error is part of the
            // control flow. Which means that the exception may be guarded.
            UnexpectedInvocationError error =
              new UnexpectedInvocationError(invocation.toString());
            ExceptionGuard.get().record(error);
            throw error;
        } else {
            logger.trace("tryDefaultInvocation", name, "invocation=", invocation, "returns", result);
            return result;
        }
    }

    @Override
    public Object invoke(Object arg0, Method arg1, Object[] arg2) throws Throwable {
        logger.trace("invoke", name, "arg0=", arg0, "arg1=", arg1, "arg2=", arg2);
        Invocation invocation = new Invocation(this, arg0, arg1, arg2);
        MockInvocationHandler handler = selectInvocationHandler();
        if (handler != null) {
            logger.trace("invoke", name, "invocation handler found");
            return handler.invoke(invocation).apply();
        } else {
            logger.trace("invoke", name, "no invocation handler found, trying default");
            return tryDefaultInvocation(invocation).apply();
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
