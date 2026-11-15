package dev.scarletnexus.ai;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<String> {
  private final HttpResponse.BodySubscriber<String> delegate =
      HttpResponse.BodySubscribers.ofString(java.nio.charset.StandardCharsets.UTF_8);
  private final int limit;
  private int received;
  private Flow.Subscription subscription;

  LimitedBodySubscriber(int limit) {
    this.limit = limit;
  }

  public CompletionStage<String> getBody() {
    return delegate.getBody();
  }

  public void onSubscribe(Flow.Subscription s) {
    subscription = s;
    delegate.onSubscribe(s);
  }

  public void onNext(List<ByteBuffer> buffers) {
    for (var b : buffers) {
      received += b.remaining();
      if (received > limit) {
        subscription.cancel();
        delegate.onError(new AIProvider.Failure("AI_RESPONSE_TOO_LARGE", false));
        return;
      }
    }
    delegate.onNext(buffers);
  }

  public void onError(Throwable t) {
    delegate.onError(t);
  }

  public void onComplete() {
    delegate.onComplete();
  }
}
