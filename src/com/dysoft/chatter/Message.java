package com.dysoft.chatter;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sean Micklethwaite
 *         22-Aug-2010
 */
public class Message {
	public final static byte VERSION = (byte)1;
	public final static Charset CHARSET = Charset.forName("UTF8");



	public interface Codec {
		Message decode(ByteBuffer buf, Manager manager) throws CodecException;
	}

	public interface Handler {
		void handle(Message msg);
	}

	public enum Type {
		HEARTBEAT(0),
		PARTY_BROADCAST(1),
		PARTY_MESSAGE(2),
		APPLICATION_CONTROL_MESSAGE(3),
		APPLICATION_DATA_MESSAGE(4),
		;

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
	
	
	final Type type;

	public Message(Type type) {
		this.type = type;
	}

	public int getEncodedLength() {
		return 2;
	}

	void encode(ByteBuffer buf) {
		buf.put(VERSION);
		buf.put(type.tag);
	}

	void handle(Handler handler) {
		handler.handle(this);
	}
	
	//// STATIC
	
	final static Map<Type, Codec> CODECS = new HashMap<Type, Codec>();
	
	public static Message decode(ByteBuffer buf, Manager manager) throws CodecException {
		byte version = buf.get();
		byte tag = buf.get();
		Type type = Type.lookup(tag);
		
		if(type != null) {
			Codec codec = CODECS.get(type);
			if(codec != null) {
				return codec.decode(buf, manager);
			} else {
				throw new CodecException("No codec found for message type: " + tag);
			}
		} else {
			throw new CodecException("Unknown message type: " + tag);
		}
	}

	public static class CodecException extends Exception {
		public CodecException(String s) {
			super(s);
		}
	}
}
