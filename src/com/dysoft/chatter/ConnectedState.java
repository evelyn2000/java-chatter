package com.dysoft.chatter;

import com.dysoft.bones.StateMachine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

/**
 * @author Sean Micklethwaite
 *         Aug 14, 2010 5:54:18 PM
 */
public class ConnectedState extends Session.NullState implements Session.State {
	final WritableByteChannel channel;

	public ConnectedState(WritableByteChannel channel) {
		this.channel = channel;
	}

	@Override
	public void send(ByteBuffer msg) throws IOException {
		channel.write(msg);
	}

	@Override
	public void exit(StateMachine.State newState) {
		try {
			channel.close();
		} catch (IOException e) {

		}
		super.exit(newState);
	}
}
