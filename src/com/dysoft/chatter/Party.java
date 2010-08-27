package com.dysoft.chatter;

import com.dysoft.bones.StateMachine;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * @author Sean Micklethwaite
 *         24-Aug-2010
 */
public class Party extends StateMachine<Party.State> {
	final long id;

	protected List<PartyMember> members;

	public Party(long id, PartyMember me) {
		super(new NullState());
		this.id = id;
		members = new ArrayList<PartyMember>(1);
		members.add(me);

		setState(new LeaderState());
	}

	public PartyMessage.Details createDetailsMessage() {
		return new PartyMessage.Details(0, id, members);
	}

	public void requestMerge(PeerSession peerSession, PartyMessage.MergeRequest msg, boolean asNewLeader) {
		getState().requestMerge(peerSession, msg, asNewLeader);
	}

	protected boolean isPartyCompatible(PartyMessage.Details details) {
		return true;
	}

	public long getID() {
		return id;
	}


	//// STATES

	public interface State extends StateMachine.State {
		public void requestMerge(PeerSession peerSession, PartyMessage.MergeRequest msg, boolean asNewLeader);
		public void confirmMerge();
	}

	class MemberState extends NullState implements State {
		/**
		 * Updates the list of party members
		 */
		public void requestMerge(PeerSession peerSession, PartyMessage.MergeRequest msg, boolean asNewLeader) {
			members = msg.getMembers();
		}
	}

	/**
	 * The current node is the leader of the party.
	 */
	class LeaderState extends NullState implements State {
		/**
		 * Locks the current party, and attempts to merge.
		 */
		public void requestMerge(PeerSession peerSession, PartyMessage.MergeRequest msg, boolean asNewLeader) {
			setState(new MergeRequestState(this, peerSession, msg, asNewLeader));
		}


	}

	public interface LockedState extends State {}

	class MergeRequestState extends StateMachine<State>.WaitState implements LockedState {
		final PeerSession peer;
		final PartyMessage.Details details;
		final boolean asNewLeader;
		final Queue<MergeRequestState> mergeQueue;

		public MergeRequestState(State parent, PeerSession peer, PartyMessage.Details details, boolean asNewLeader, Queue<MergeRequestState> mergeQueue) {
			super(parent, parent, 1000);
			this.peer = peer;
			this.details = details;
			this.asNewLeader = asNewLeader;
			this.mergeQueue = mergeQueue;
		}

		public MergeRequestState(State parent, PeerSession peer, PartyMessage.Details details, boolean asNewLeader) {
			this(parent, peer, details, asNewLeader, new ArrayDeque<MergeRequestState>());
		}

		/**
		 * Queues merge request until party is unlocked.
		 */
		public void requestMerge(PeerSession peerSession, PartyMessage.MergeRequest msg, boolean asNewLeader) {
			mergeQueue.add(new MergeRequestState((State)parent, peerSession, msg, asNewLeader, mergeQueue));
		}

		public void confirmMerge() {

		}

		@Override
		public void enter(StateMachine.State oldState) {
			super.enter(oldState);

			if(isPartyCompatible(details)) {
				peer.acceptParty(details);
			} else {
				setState((State)parent);
			}
		}

		@Override
		public void exit(StateMachine.State newState) {
			if(!newState.containsState(LockedState.class) && !mergeQueue.isEmpty()) {
				setState(mergeQueue.remove());
			}
			super.exit(newState);
		}

		@Override
		public boolean containsState(StateMachine.State state) {
			return this == state;
		}
	}

	static class NullState extends StateMachine.NullState implements State {
		public void requestMerge(PeerSession peerSession, PartyMessage.MergeRequest msg, boolean asNewLeader) {
		}

		public void confirmMerge() {
		}
	}
}
