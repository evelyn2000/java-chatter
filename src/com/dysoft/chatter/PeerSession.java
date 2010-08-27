package com.dysoft.chatter;

import com.dysoft.bones.StateMachine;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sean Micklethwaite
 *         22-Aug-2010
 */
public class PeerSession extends StateMachine<PeerSession.State> implements TransportSession {
	protected final static Logger LOG = Logger.getLogger(PeerSession.class);
	static final int TIMEOUT = 3000;

	final PeerManager manager;
	final Transport transport;

	public PeerSession(PeerManager manager, Transport transport) {
		super(new NullState());
		this.manager = manager;
		this.transport = transport;
	}

	void startClient() {
		setState(new ClientBeginState(getState()));
	}

	void startServer() {
		setState(new ServerBeginState(getState()));
	}

	public void onReceive(ByteBuffer data) {
		try {
			Message msg = Message.decode(data, manager);
			LOG.debug("Received " + msg);
			msg.handle(getState());
		} catch (Message.CodecException e) {
			LOG.error("Message decode failed", e);
			setState(new ClosingState("Message decode failed"));
		}
	}

	public void close() {
		setState(new ClosingState("Session explicitly closed"));
	}

	public void onClose() {
		setState(new ClosedState());
	}

	protected void send(Message msg) throws IOException {
		ByteBuffer buf = ByteBuffer.allocate(msg.getEncodedLength());
		msg.encode(buf);
		buf.flip();
		transport.send(buf);
	}

	public void acceptParty(PartyMessage.Details details) {

	}


	//// STATES

	public interface State extends StateMachine.State, PartyMessage.Handler {
	}

	class WaitState extends StateMachine.WaitState implements State {
		WaitState(State parent, State timeoutState) {
			super(parent, timeoutState, TIMEOUT);
		}

		public void handle(Message msg) {
		}

		public void handle(PartyMessage.Details msg) {
		}

		public void handle(PartyMessage.MergeRequest msg) {
		}

		public void handle(PartyMessage.MergeConfirm msg) {
		}
	}

	class PartyState extends BaseState {
		final Party party;

		PartyState(State parent, Party party) {
			super(parent);
			this.party = party;
		}
	}

	class ClientBeginState extends WaitState {
		ClientBeginState(State parent) {
			super(parent, new ClosingState("Timeout waiting for reply to party details"));
		}

		@Override
		public void enter(StateMachine.State oldState) {
			super.enter(oldState);
			for(Party party : manager.getParties()) {
				try {
					send(party.createDetailsMessage());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		@Override
		public void handle(PartyMessage.MergeRequest msg) {
			Party party = manager.getParty(msg.getClientPartyID());
			if(party == null) {
				setState(new ClosingState("Invalid client party ID: " + msg.getClientPartyID()));
			} else {
				party.requestMerge(PeerSession.this, msg, false);
			}
		}
	}

	class ServerBeginState extends WaitState {
		ServerBeginState(State parent) {
			super(parent, new ClosingState("Timeout waiting for party details"));
		}
	}

	protected static class NullState extends StateMachine.NullState implements State {
		public void handle(Message msg) {
		}

		public void handle(PartyMessage.Details msg) {
		}

		public void handle(PartyMessage.MergeRequest msg) {
		}

		public void handle(PartyMessage.MergeConfirm msg) {
		}
	}

	protected class ClosingState extends NullState implements State {
		final String reason;

		public ClosingState(String reason) {
			this.reason = reason;
		}

		@Override
		public void enter(StateMachine.State newState) {
			try {
				LOG.info("Closing session: " + reason);
				transport.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private class ClosedState extends NullState {

	}
}
