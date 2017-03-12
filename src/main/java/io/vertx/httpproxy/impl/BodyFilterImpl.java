package io.vertx.httpproxy.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.BodyFilter;

import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class BodyFilterImpl implements BodyFilter {

  final Function<ReadStream<Buffer>, ReadStream<Buffer>> filter;

  public BodyFilterImpl(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter) {
    this.filter = filter;
  }

  @Override
  public ReadStream<Buffer> apply(ReadStream<Buffer> bufferReadStream) {
    return filter.apply(bufferReadStream);
  }
}
