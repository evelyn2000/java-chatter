package com.dysoft.chatter;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Sean Micklethwaite
 *         24-Aug-2010
 */
public class PartyMessage extends Message {
	final Type type;

	public PartyMessage(Type type) {
		super(Message.Type.PARTY_MESSAGE);
		this.type = type;
	}

	public enum Type {
		DETAILS(0), MERGE_REQUEST(1), MERGE_CONFIRM(2);

		final static Map<Byte, Type> typeMap = new HashMap<Byte, Type>();
		static {
			for(Type t : values()) {
				typeMap.put(t.tag, t);
			}
		}

		public final byte tag;

		Type(int tag) {
			this.tag = (byte)tag;
		}

		public static Type lookup(byte tag) {
			return typeMap.get(tag);
		}
	}

	public interface Handler extends Message.Handler {
		void handle(Details msg);
		void handle(MergeRequest msg);
		void handle(MergeConfirm msg);
	}

	public static class Details extends PartyMessage {
		final long partyFormatID; //< Application specific
		final long partyID;
		final List<PartyMember> members;

		protected Details(Type type, long partyFormatID, long partyID, List<PartyMember> members) {
			super(type);
			this.partyFormatID = partyFormatID;
			this.partyID = partyID;
			this.members = members;
		}

		public Details(long partyFormatID, long partyID, List<PartyMember> members) {
			this(Type.DETAILS, partyFormatID,partyID,members);
		}

		public Details(Type type, ByteBuffer buf, PeerManager manager) {
			super(type);
			partyFormatID = buf.getLong();
			partyID = buf.getLong();
			PartyCodec partyCodec = manager.getPartyCodec(partyFormatID);

			int numMembers = buf.getInt();
			members = new ArrayList<PartyMember>(numMembers);
			while(numMembers-- > 0) {
				int memberSize = buf.getInt();
				members.add(partyCodec.decodePartyMember(buf, memberSize));
			}
		}

		public int getEncodedLength() {
			int tot = super.getEncodedLength() + 21;
			for(PartyMember m : members) {
				tot += m.getEncodedLength() + 4;
			}
			return tot;
		}

		@Override
		public void encode(ByteBuffer buf) {
			super.encode(buf);
			buf.put(type.tag);
			buf.putLong(partyFormatID);
			buf.putLong(partyID);
			buf.putInt(members.size());
			for(PartyMember m : members) {
				buf.putInt(m.getEncodedLength());
				m.encode(buf);
			}
		}

		public long getPartyFormatID() {
			return partyFormatID;
		}

		public long getPartyID() {
			return partyID;
		}

		public List<PartyMember> getMembers() {
			return members;
		}
	}

	public static class MergeRequest extends Details {
		final long clientPartyID;

		public MergeRequest(long partyFormatID, long partyID, long clientPartyID, List<PartyMember> members) {
			super(Type.MERGE_REQUEST, partyFormatID, partyID, members);
			this.clientPartyID = clientPartyID;
		}

		public MergeRequest(ByteBuffer buf, PeerManager manager) {
			super(Type.MERGE_REQUEST, buf, manager);
			clientPartyID = buf.getLong();
		}

		@Override
		public int getEncodedLength() {
			return super.getEncodedLength() + 8;
		}

		@Override
		public void encode(ByteBuffer buf) {
			super.encode(buf);
			buf.putLong(clientPartyID);
		}

		public long getClientPartyID() {
			return clientPartyID;
		}
	}

	public static class MergeConfirm extends Details {
		public MergeConfirm(long partyFormatID, long partyID, List<PartyMember> members) {
			super(Type.MERGE_CONFIRM, partyFormatID, partyID, members);
		}

		public MergeConfirm(ByteBuffer buf, PeerManager manager) {
			super(Type.MERGE_CONFIRM, buf, manager);
		}
	}

	static {
		Message.CODECS.put(Message.Type.PARTY_MESSAGE, new Codec() {
			public Message decode(ByteBuffer buf, Manager manager) throws CodecException {
				byte tag = buf.get();
				if(tag == Type.DETAILS.tag) {
					return new Details(Type.DETAILS, buf, (PeerManager)manager);
				} else if(tag == Type.MERGE_REQUEST.tag) {
					return new MergeRequest(buf, (PeerManager)manager);
				} else if(tag == Type.MERGE_CONFIRM.tag) {
					return new MergeConfirm(buf, (PeerManager)manager);
				} else {
					throw new CodecException("Unkown PartyMessage type: " + tag);
				}
			}
		});
	}
}
