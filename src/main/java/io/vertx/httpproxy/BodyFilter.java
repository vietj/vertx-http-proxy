package io.vertx.httpproxy;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.impl.BodyFilterImpl;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface BodyFilter extends Function<ReadStream<Buffer>, ReadStream<Buffer>> {

  static BodyFilter create(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter) {
    return new BodyFilterImpl(filter);
  }
}
