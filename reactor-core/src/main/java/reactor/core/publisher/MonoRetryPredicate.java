/*
 * Copyright (c) 2011-2017 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core.publisher;

import java.util.Objects;
import java.util.function.Predicate;

import reactor.core.CoreSubscriber;

/**
 * Repeatedly subscribes to the source if the predicate returns true after completion of
 * the previous subscription.
 *
 * @param <T> the value type
 * @see <a href="https://github.com/reactor/reactive-streams-commons">Reactive-Streams-Commons</a>
 */
final class MonoRetryPredicate<T> extends MonoOperator<T, T> {

	final Predicate<? super Throwable> predicate;

	MonoRetryPredicate(Mono<? extends T> source,
			Predicate<? super Throwable> predicate) {
		super(source);
		this.predicate = Objects.requireNonNull(predicate, "predicate");
	}

	@Override
	public void subscribe(CoreSubscriber<? super T> s) {

		FluxRetryPredicate.RetryPredicateSubscriber<T> parent =
				new FluxRetryPredicate.RetryPredicateSubscriber<>(source, s, predicate);

		s.onSubscribe(parent);

		if (!parent.isCancelled()) {
			parent.resubscribe();
		}
	}
}
