/* **************************************************************************
 * Copyright (C) 2010-2011 VMware, Inc. All rights reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0.
 * Please see the LICENSE file to review the full text of the Apache License 2.0.
 * You may not use this product except in compliance with the License.
 * ************************************************************************** */
package com.vmware.lmock.exception;

/**
 * The user defines an illegal range for occurrences.
 */
public final class IllegalOccurrencesDefinitionException extends ExpectationCreationException {
    /** Class version, for serialization. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception, along with an error message.
     *
     * @param msg
     *            the error message
     */
    public IllegalOccurrencesDefinitionException(String msg) {
        super(msg);
    }
}
