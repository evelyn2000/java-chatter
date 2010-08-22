package com.dysoft.chatter;

import com.dysoft.bones.StateMachine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Sean Micklethwaite
 *         Aug 14, 2010 4:46:51 PM
 */
public class Session extends StateMachine<Session.State> implements TransportSession {

	public Session() {
		super(new NullState());
	}

	public void send(ByteBuffer data) throws IOException {

	}

	public void onReceive(ByteBuffer data) {
		getState().onReceive(data);
	}

	public void close() {
		setState(new ClosedState());
	}

	public void onClose() {
		getState().onClose();
	}


	public interface State extends StateMachine.State, TransportSession {
	}


	public static class NullState extends StateMachine.NullState implements State {
		public void send(ByteBuffer msg) throws IOException {
		}

		public void onReceive(ByteBuffer data) {

		}

		public void onClose() {

		}
	}

	public class BaseState extends StateMachine<State>.BaseState implements State {
		public BaseState(State parent) {
			super(parent);
		}

		public void onReceive(ByteBuffer data) {
			parent.onReceive(data);
		}

		public void onClose() {
			parent.onClose();
		}
	}

	public class WaitState extends BaseState {
		final State timeoutState;
		final long delay;

		public WaitState(State parent, State timeoutState, long delay) {
			super(parent);
			this.timeoutState = timeoutState;
			this.delay = delay;
		}

		@Override
		public void enter(StateMachine.State oldState) {
			super.enter(oldState);
			new Timer().schedule(new TimerTask() {
				@Override
				public void run() {
					setState(timeoutState);
				}
			}, delay);
		}
	}

	private class ClosedState extends NullState {
	}
}
