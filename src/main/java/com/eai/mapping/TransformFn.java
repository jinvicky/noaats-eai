package com.eai.mapping;

import java.util.List;

public interface TransformFn {
    String name();
    Object apply(Object input, List<Object> args, TransformContext ctx);
}
