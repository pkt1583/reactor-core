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
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.logging.Level;
import javax.annotation.Nullable;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.util.Logger;
import reactor.util.Loggers;


/**
 * A push of overridable lifecycle hooks that can be used for cross-cutting
 * added behavior on {@link Flux}/{@link Mono} operators.
 */
public abstract class Hooks {

	/**
	 * Override global error dropped strategy which by default bubble back the error.
	 *
	 * @param c the {@link Consumer} to apply to dropped errors
	 */
	public static void onErrorDropped(Consumer<? super Throwable> c) {
		Objects.requireNonNull(c, "onErrorDroppedHook");
		if(log.isDebugEnabled()) {
			log.debug("Hooking new default : onErrorDropped");
		}

		synchronized(log) {
			if (onErrorDroppedHook != null) {
				@SuppressWarnings("unchecked")
				Consumer<Throwable> _c = ((Consumer<Throwable>)onErrorDroppedHook).andThen(c);
				onErrorDroppedHook = _c;
			}
			else {
				onErrorDroppedHook = c;
			}
		}
	}

	/**
	 * Override global data dropped strategy which by default throw {@link
	 * reactor.core.Exceptions#failWithCancel()}
	 *
	 * @param c the {@link Consumer} to apply to data (onNext) that is dropped
	 */
	public static void onNextDropped(Consumer<Object> c) {
		Objects.requireNonNull(c, "onNextDroppedHook");
		if(log.isDebugEnabled()) {
			log.debug("Hooking new default : onNextDropped");
		}

		synchronized(log) {
			if (onNextDroppedHook != null) {
				onNextDroppedHook = onNextDroppedHook.andThen(c);
			}
			else {
				onNextDroppedHook = c;
			}
		}
	}

	/**
	 * Configure a global chain of "assembly" hooks to intercept signals produced by the
	 * passed {@link Publisher} ({@link Flux} or {@link Mono}). The passed function provides
	 * a base {@link OperatorHook} that can be used as a kind of builder to compose several
	 * hooks or behaviors (eg. transverse logging). {@link OperatorHook#ignore()} can be
	 * used to prevent all hooks from applying to a specific {@link Publisher}.
	 * <p>
	 * Can be reset via {@link #resetOnOperator()}
	 *
	 * @param onOperator a callback for each assembly that must return a non-null {@link OperatorHook}
	 * @param <T> the arbitrary assembled sequence type
	 */
	public static <T> void onOperator(Function<? super OperatorHook<T>, ? extends OperatorHook<T>> onOperator) {
		Objects.requireNonNull(onOperator, "onOperator");
		if(log.isDebugEnabled()) {
			log.debug("Hooking new default : onOperator");
		}

		synchronized(log) {
			if (onOperatorHook != null) {
				@SuppressWarnings("unchecked")
				OnOperatorHook<T> hook = (OnOperatorHook<T>)onOperatorHook;

				onOperatorHook = new OnOperatorHook<>(hook.hook.andThen(onOperator));
			}
			else {
				onOperatorHook = new OnOperatorHook<>(onOperator);
			}
		}
	}

	/**
	 * Override global operator error mapping which by default add as suppressed exception
	 * either data driven exception or error driven exception.
	 *
	 * @param f an operator error {@link BiFunction} mapper, returning an arbitrary exception
	 * given the failure and optionally some original context (data or error).
	 */
	public static void onOperatorError(BiFunction<? super Throwable, Object, ? extends Throwable> f) {
		Objects.requireNonNull(f, "onOperatorErrorHook");
		if(log.isDebugEnabled()) {
			log.debug("Hooking new default : onOperatorError");
		}
		synchronized(log) {
			if (onOperatorErrorHook != null) {
				BiFunction<? super Throwable,Object,? extends Throwable> ff = onOperatorErrorHook;
				onOperatorErrorHook = (e, data) -> f.apply(ff.apply(e, data), data);
			}
			else {
				onOperatorErrorHook = f;
			}
		}
	}

	/**
	 * Set a global "subscription" hook to intercept signals produced by the passed
	 * terminal {@link CoreSubscriber}. The passed function must result in a value different from null.
	 * <p>
	 * Can be reset via {@link #resetOnNewSubscriber()} ()}
	 *
	 * @param onSubscriber a callback for each terminal {@link Publisher#subscribe(Subscriber)}
	 * @param <T> the arbitrary subscribed sequence type
	 */
	public static <T> void onNewSubscriber(BiFunction<? super Publisher<T>, ? super CoreSubscriber<T>, ? extends CoreSubscriber<T>> onSubscriber) {
		Objects.requireNonNull(onSubscriber, "onNewSubscriber");
		if (log.isDebugEnabled()) {
			log.debug("Hooking new default : onNewSubscriber");
		}


		@SuppressWarnings("unchecked") BiFunction<? super Publisher<?>, ? super CoreSubscriber<?>, ? extends CoreSubscriber<?>> _onSubscriberHook =
				(BiFunction<? super Publisher<?>, ? super CoreSubscriber<?>, ? extends CoreSubscriber<?>>) onSubscriber;
		synchronized(log) {
			if (onSubscriberHook != null) {
				@SuppressWarnings("unchecked")
				BiFunction<? super Publisher<?>, ? super CoreSubscriber<?>, ? extends CoreSubscriber<?>> ff = onSubscriberHook;

				onSubscriberHook = (p, s) -> _onSubscriberHook.apply(p, ff.apply(p, s));
			}
			else {
				onSubscriberHook = _onSubscriberHook;
			}
		}
	}

	/**
	 * Reset global error dropped strategy to bubbling back the error.
	 */
	public static void resetOnErrorDropped() {
		if(log.isDebugEnabled()) {
			log.debug("Reset to factory defaults : onErrorDropped");
		}
		synchronized (log) {
			onErrorDroppedHook = null;
		}
	}

	/**
	 * Reset global data dropped strategy to throwing via {@link
	 * reactor.core.Exceptions#failWithCancel()}
	 */
	public static void resetOnNextDropped() {
		if(log.isDebugEnabled()) {
			log.debug("Reset to factory defaults : onNextDropped");
		}
		synchronized (log) {
			onNextDroppedHook = null;
		}
	}

	/**
	 * Reset global "assembly" hook tracking
	 */
	public static void resetOnOperator() {
		if(log.isDebugEnabled()) {
			log.debug("Reset to factory defaults : onOperator");
		}
		synchronized (log) {
			onOperatorHook = null;
		}
	}

	/**
	 * Reset global "subscriber" hook tracking
	 */
	public static void resetOnNewSubscriber() {
		if (log.isDebugEnabled()) {
			log.debug("Reset to factory defaults : onNewSubscriber");
		}
		synchronized (log) {
			onSubscriberHook = null;
		}
	}

	/**
	 * Reset global operator error mapping to adding as suppressed exception.
	 */
	public static void resetOnOperatorError() {
		if(log.isDebugEnabled()) {
			log.debug("Reset to factory defaults : onOperatorError");
		}
		synchronized (log) {
			onOperatorErrorHook = null;
		}
	}

	/**
	 * Filtering and Handling options to apply on a given {@link Publisher}
	 * @param <T> arbitrary sequence type
	 */
	@SuppressWarnings("unchecked")
	public static final class OperatorHook<T> {

		/**
		 * Peek into sequence signals.
		 * <p>
		 * The callbacks are all optional.
		 *
		 * @param onNextCall A consumer that will observe {@link Subscriber#onNext(Object)}
		 * @param onErrorCall A consumer that will observe {@link Subscriber#onError(Throwable)}}
		 * @param onCompleteCall A task that will run on {@link Subscriber#onComplete()}
		 * @param onAfterTerminateCall A task will run after termination via {@link Subscriber#onComplete()} or {@link Subscriber#onError(Throwable)}
		 * @return an observing {@link OperatorHook}
		 */
		public final OperatorHook<T> doOnEach(
				@Nullable Consumer<? super T> onNextCall,
				@Nullable Consumer<? super Throwable> onErrorCall,
				@Nullable Runnable onCompleteCall,
				@Nullable Runnable onAfterTerminateCall) {
			return doOnSignal(new FluxPeek<>(Flux.never(),
					null,
					onNextCall,
					onErrorCall,
					onCompleteCall,
					onAfterTerminateCall, null, null));
		}

		/**
		 * Peek into lifecycle signals.
		 * <p>
		 * The callbacks are all optional.
		 *
		 * @param onSubscribeCall A consumer that will observe {@link Subscriber#onSubscribe(Subscription)}
		 * @param onRequestCall A consumer of long that will observe {@link Subscription#request(long)}}
		 * @param onCancelCall A task that will run on {@link Subscription#cancel()}
		 * @return an observing {@link OperatorHook}
		 */
		public final OperatorHook<T> doOnLifecycle(
				@Nullable Consumer<? super Subscription> onSubscribeCall,
				@Nullable LongConsumer onRequestCall,
				@Nullable Runnable onCancelCall) {
			return doOnSignal(new FluxPeek<>(Flux.never(),
					onSubscribeCall,
					null,
					null,
					null,
					null, onRequestCall, onCancelCall));
		}

		final OperatorHook<T> doOnSignal(SignalPeek<T> log){
			if(publisher == null || this == IGNORE || publisher instanceof
					ConnectableFlux){
				return this;
			}
			if (publisher instanceof Mono) {
				if (publisher instanceof Fuseable) {
					return new OperatorHook<>(new MonoLogFuseable<>((Mono)publisher, log),
							traced, tracedCategory, tracedLevel, tracedSignals);
				}
				else {
					return new OperatorHook<>(new MonoLog<>((Mono)publisher, log), traced
							, tracedCategory, tracedLevel, tracedSignals);
				}
			}
			else if (publisher instanceof ParallelFlux) {
				return new OperatorHook<>(new ParallelLog<>((ParallelFlux<T>) publisher, log), traced
						, tracedCategory, tracedLevel, tracedSignals);
			}
			else if (publisher instanceof Fuseable) {
				return new OperatorHook<>(new FluxLogFuseable<>((Flux<T>)publisher, log), traced
						, tracedCategory, tracedLevel,
						tracedSignals);
			}
			else {
				return new OperatorHook<>(new FluxLog<>((Flux<T>)publisher,log), traced
						, tracedCategory, tracedLevel, tracedSignals);
			}
		}

		/**
		 * Discard all {@link OperatorHook} applied to the current {@link #publisher()}
		 *
		 * @return an ignoring {@link OperatorHook}
		 */
		@SuppressWarnings("unchecked")
		public final OperatorHook<T> ignore(){
			return OperatorHook.IGNORE;
		}

		/**
		 * Apply hook only if {@link #publisher()} is {@link Flux}
		 * @return a {@link OperatorHook} ignoring source that are not {@link Flux}
		 */
		public final OperatorHook<T> ifFlux(){
			return publisher() instanceof Flux ? this : OperatorHook.IGNORE;
		}

		/**
		 * Apply hook only if {@link #publisher()} is {@link Mono}
		 * @return a {@link OperatorHook} ignoring source that are not {@link Mono}
		 */
		public final OperatorHook<T> ifMono(){
			return publisher() instanceof Mono ? this : OperatorHook.IGNORE;
		}

		/**
		 * Apply hook only if {@link #publisher()} is {@link ParallelFlux}
		 * @return a {@link OperatorHook} ignoring source that are not {@link ParallelFlux}
		 */
		public final OperatorHook<T> ifParallelFlux(){
			return publisher() instanceof ParallelFlux ? this : OperatorHook.IGNORE;
		}

		/**
		 * Apply hook only if one of the provided names matches the
		 * operator underlying type name (case insensitive, without Mono/Flux prefix
		 * or Fuseable suffix).
		 *
		 * @param names a list of possible names that would match if equal
		 * @return a possibly ignoring {@link OperatorHook}
		 */
		public final OperatorHook<T> ifName(String... names) {
			if(this == IGNORE) return this;
			String className = publisher().getClass()
			                              .getSimpleName()
			                              .replaceAll("Flux|Mono|Parallel|Fuseable", "");
			for (String name : names) {
				if (className.equalsIgnoreCase(name)) {
					return this;
				}
			}
			return OperatorHook.IGNORE;
		}

		/**
		 * Apply hook only if one of the provided names is contained in the
		 * operator's underlying type name (case insensitive, without Mono/Flux prefix
		 * or Fuseable suffix).
		 *
		 * @param names a list of possible names that would match if contained
		 * @return a possibly ignoring {@link OperatorHook}
		 */
		public final OperatorHook<T> ifNameContains(String... names){
			if(this == IGNORE) return this;
			String className = publisher().getClass()
			                              .getSimpleName()
			                              .replaceAll("Flux|Mono|Parallel|Fuseable", "")
			                              .toLowerCase();

			for (String name : names) {
				if (className.contains(name.toLowerCase())) {
					return this;
				}
			}
			return OperatorHook.IGNORE;
		}

		/**
		 * Observe Reactive Streams signals matching the passed filter {@code options} and
		 * log them at {@literal INFO} level.
		 * <p>
		 * Logging it done through {@link Logger} so the default logging framework is
		 * {@code java.util.logging}, but if SLF4J is available it will be used instead.
		 * <p>
		 * Options allow fine grained filtering of the traced signal, for instance to only
		 * capture onNext and onError:
		 * <pre>
		 *     Operators.signalLogger(source, "category", Level.INFO, SignalType.ON_NEXT,
		 * SignalType.ON_ERROR)
		 *
		 * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.M2/src/docs/marble/log.png"
		 * alt="">
		 *
		 * @param category to be mapped into logger configuration (e.g.
		 * org.springframework.reactor). If category is null, empty or ends with "." like
		 * "reactor.", a generated operator suffix will complete, e.g. "reactor.Flux.Map".
		 * @param options a vararg {@link SignalType} option to filter log messages
		 *
		 * @return a logging {@link OperatorHook}
		 */
		public OperatorHook<T> log(@Nullable String category, SignalType... options){
			return log(category, Level.INFO, options);
		}

		/**
		 * Observe Reactive Streams signals matching the passed filter {@code options} and
		 * log them at {@literal INFO} level.
		 * <p>
		 * Logging it done through {@link Logger} so the default logging framework is
		 * {@code java.util.logging}, but if SLF4J is available it will be used instead.
		 * <p>
		 * Options allow fine grained filtering of the traced signal, for instance to only
		 * capture onNext and onError:
		 * <pre>
		 *     Operators.signalLogger(source, "category", Level.INFO, SignalType.ON_NEXT,
		 * SignalType.ON_ERROR)
		 *
		 * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.M2/src/docs/marble/log.png"
		 * alt="">
		 *
		 * @param category to be mapped into logger configuration (e.g.
		 * org.springframework.reactor). If category is null, empty or ends with "." like
		 * "reactor.", a generated operator suffix will complete, e.g. "reactor.Flux.Map".
		 * @param showOperatorLine capture the current stack to display operator
		 * class/line number.
		 * @param options a vararg {@link SignalType} option to filter log messages
		 *
		 * @return a logging {@link OperatorHook}
		 */
		public OperatorHook<T> log(@Nullable String category, boolean showOperatorLine,
				SignalType... options){
			return log(category, Level.INFO, showOperatorLine, options);
		}

		/**
		 * Observe Reactive Streams signals matching the passed filter {@code options}and
		 * log them at the provided level.
		 * <p>
		 * Logging it done through {@link Logger} so the default logging framework is
		 * {@code java.util.logging}, but if SLF4J is available it will be used instead.
		 * <p>
		 * Options allow fine grained filtering of the traced signal, for instance to only
		 * capture onNext and onError:
		 * <pre>
		 *     Operators.signalLogger(source, "category", Level.INFO, SignalType.ON_NEXT,
		 * SignalType.ON_ERROR)
		 *
		 * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.M2/src/docs/marble/log.png"
		 * alt="">
		 *
		 * @param category to be mapped into logger configuration (e.g.
		 * org.springframework.reactor). If category is null, empty or ends with "." like
		 * "reactor.", a generated operator suffix will complete, e.g. "reactor.Flux.Map".
		 * @param level the level to enforce for this tracing Flux
		 * @param options a vararg {@link SignalType} option to filter log messages
		 *
		 * @return a logging {@link OperatorHook}
		 */
		public OperatorHook<T> log(@Nullable String category, Level level, SignalType... options){
			Objects.requireNonNull(level, "level");
			if(publisher == null){
				return this;
			}
			return doOnSignal(new SignalLogger<>(publisher, category, level, false,
					options));
		}

		/**
		 * Observe Reactive Streams signals matching the passed filter {@code options} and
		 * log them at the provided level.
		 * <p>
		 * Logging it done through {@link Logger} so the default logging framework is
		 * {@code java.util.logging}, but if SLF4J is available it will be used instead.
		 * <p>
		 * Options allow fine grained filtering of the traced signal, for instance to only
		 * capture onNext and onError:
		 * <pre>
		 *     Operators.signalLogger(source, "category", Level.INFO, SignalType.ON_NEXT,
		 * SignalType.ON_ERROR)
		 *
		 * <img class="marble" src="https://raw.githubusercontent.com/reactor/reactor-core/v3.1.0.M2/src/docs/marble/log.png"
		 * alt="">
		 *
		 * @param category to be mapped into logger configuration (e.g.
		 * org.springframework.reactor). If category is null, empty or ends with "." like
		 * "reactor.", a generated operator suffix will complete, e.g. "reactor.Flux.Map".
		 * @param level the level to enforce for this tracing Flux
		 * @param showOperatorLine capture the current stack to display operator
		 * class/line number.
		 * @param options a vararg {@link SignalType} option to filter log messages
		 *
		 * @return a logging {@link OperatorHook}
		 */
		public OperatorHook<T> log(@Nullable String category, Level level, boolean showOperatorLine,
				SignalType... options){
			if(this == IGNORE) return this;
			Objects.requireNonNull(level, "level");
			if(showOperatorLine){
				tracedCategory = category;
				tracedLevel = level;
				tracedSignals = options;
				return this;
			}
			return log(category, level, options);
		}

		/**
		 * Enable operator stack recorder that captures a declaration stack whenever an
		 * operator is instantiated. When errors are observed later on, they will be
		 * enriched with a Suppressed Exception detailing the original assembly line stack.
		 * Must be called before producers (e.g. Flux.map, Mono.fromCallable) are actually
		 * called to intercept the right stack information.
		 *
		 * @return a operator stack capturing {@link OperatorHook}
		 */
		public OperatorHook<T> operatorStacktrace(){
			if(this == IGNORE) return this;
			traced = true;
			return this;
		}

		/**
		 * The publisher being decorated
		 *
		 * @return The publisher being decorated
		 */
		public Publisher<T> publisher() {
			return publisher;
		}

		@SuppressWarnings("ConstantConditions")
		static final OperatorHook IGNORE = new OperatorHook(null);

		final Publisher<T> publisher;

		String tracedCategory;
		Level tracedLevel;
		SignalType[] tracedSignals;

		boolean traced;

		OperatorHook(Publisher<T> p) {
			this(p, false, null, null, null);
		}

		OperatorHook(Publisher<T> p,
				boolean traced,
				@Nullable String tracedCategory,
				@Nullable Level tracedLevel,
				@Nullable SignalType[] tracedSignals) {
			this.traced = traced;
			this.publisher = p;
			this.tracedSignals = tracedSignals;
			this.tracedLevel = tracedLevel;
			this.tracedCategory = tracedCategory;
		}
	}

	static volatile OnOperatorHook<?>                                                                       onOperatorHook;
	static volatile Consumer<? super Throwable>                                                             onErrorDroppedHook;
	static volatile Consumer<Object>                                                                        onNextDroppedHook;
	static volatile BiFunction<? super Throwable, Object, ? extends Throwable>                              onOperatorErrorHook;
	static volatile BiFunction<? super Publisher<?>, ? super CoreSubscriber<?>, ? extends CoreSubscriber<?>>onSubscriberHook;

	static {
		boolean globalTrace =
				Boolean.parseBoolean(System.getProperty("reactor.trace.operatorStacktrace",
						"false"));

		if (globalTrace) {
			onOperatorHook = new OnOperatorHook<>(OperatorHook::operatorStacktrace);
		}
	}

	Hooks() {
	}

	final static class OnOperatorHook<T>
			implements Function<Publisher<T>, Publisher<T>> {

		final Function<? super OperatorHook<T>, ? extends OperatorHook<T>> hook;

		OnOperatorHook(Function<? super OperatorHook<T>, ? extends OperatorHook<T>> hook) {
			this.hook = hook;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Publisher<T> apply(Publisher<T> publisher) {
			if (!(publisher instanceof ConnectableFlux)) {
				OperatorHook<T> hooks =
						Objects.requireNonNull(hook.apply(new OperatorHook<>(publisher)), "hook");

				if (hooks.tracedLevel != null) {

					hooks = hooks.doOnSignal(new SignalLogger<>(publisher, hooks
							.tracedCategory, hooks.tracedLevel, true, hooks
							.tracedSignals));
				}

				if (hooks != OperatorHook.IGNORE) {
					publisher = hooks.publisher;


					boolean trace = hooks.traced;

					if (trace){
						if (publisher instanceof Callable) {
							if (publisher instanceof Mono) {
								return new MonoCallableOnAssembly<>((Mono<T>)publisher);
							}
							return new FluxCallableOnAssembly<>((Flux<T>)publisher);
						}
						if (publisher instanceof Mono) {
							return new MonoOnAssembly<>((Mono<T>)publisher);
						}
						if (publisher instanceof ParallelFlux){
							return new ParallelFluxOnAssembly<>((ParallelFlux<T>) publisher);
						}
						return new FluxOnAssembly<>((Flux<T>)publisher);
					}
					return publisher;
				}
			}
			if (hook != null && publisher instanceof ConnectableFlux) {
				return new ConnectableFluxOnAssembly<>((ConnectableFlux<T>) publisher);
			}
			return publisher;
		}
	}

	static final Logger log = Loggers.getLogger(Hooks.class);
}
