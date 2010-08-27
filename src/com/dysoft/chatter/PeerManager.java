package com.dysoft.chatter;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.*;

/**
 * @author Sean Micklethwaite
 *         Aug 14, 2010 7:47:54 PM
 *
 * This class sends out party broadcasts, and accepts incoming TCP
 * connections. It also receives UDP data and sends it to the appropriate
 * party.
 */
public class PeerManager extends Manager implements Manager.Dispatcher, TransportSession {
	protected final static Logger LOG = Logger.getLogger(PeerManager.class);

	DatagramChannel dataChannel;
	MulticastSocket broadcaster;
	SocketTransport dataTransport;
	
	Timer broadcastTimer = new Timer();

	final Map<SocketAddress, PeerSession> peers = new HashMap<SocketAddress, PeerSession>();
	final Map<Long, Party> parties = new HashMap<Long, Party>();

	public PeerManager() throws IOException {
	}

	/**
	 * Starts listening for broadcasts and data on the specified UDP port,
	 * and accepting peer connections on the TCP port.
	 * @param udpPort
	 * @param tcpPort
	 * @throws IOException If ports are in use, etc.
	 */
	public void listen(int udpPort, int tcpPort) throws IOException {
		listen("0.0.0.0", tcpPort, this);

		broadcaster = new MulticastSocket();

		dataChannel = DatagramChannel.open();
		dataChannel.socket().bind(new InetSocketAddress(udpPort));
		dataChannel.socket().setBroadcast(true);
		dataChannel.configureBlocking(false);
	}

	public void startBroadcasting() {
		broadcastTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				SocketAddress src = null;
				try {
					ByteBuffer buf = ByteBuffer.allocate(128);
					src = dataChannel.receive(buf);

					if(src != null) {
						buf.flip();
						System.out.println("GOT PACKET from " + src);
						connect(new InetSocketAddress(((InetSocketAddress)src).getAddress(), 1234), PeerManager.this);
						return;
					}

					Message msg = new Message(Message.Type.PARTY_BROADCAST);

					msg.encode(buf);
					buf.flip();
					dataChannel.send(buf, new InetSocketAddress("255.255.255.255", 12345));
				} catch (IOException e) {
					e.printStackTrace();
				}

				startBroadcasting();
			}
		}, 1000);
	}


	//// Dispatcher

	public TransportSession accept(Transport channel) {
		LOG.info("Remote: Connected to " + channel.getSocketAddress());
		PeerSession peer = new PeerSession(this, channel);
		peers.put(channel.getSocketAddress(), peer);
		peer.startServer();
		return peer;
	}

	public TransportSession onConnect(Transport channel) {
		LOG.info("Connected to " + channel.getSocketAddress());
		PeerSession peer = new PeerSession(this, channel);
		peers.put(channel.getSocketAddress(), peer);
		peer.startClient();
		return null;
	}


	//// TransportSession - UDP Data

	public void onReceive(ByteBuffer data) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	public void onClose() {
		//To change body of implemented methods use File | Settings | File Templates.
	}


	public Collection<Party> getParties() {
		return parties.values();
	}

	public Party getParty(long clientPartyID) {
		return parties.get(clientPartyID);
	}

	public void setParty(Party party) {
		parties.put(party.getID(), party);
	}

	public PartyCodec getPartyCodec(long partyFormatID) {
		// TODO
		return new PartyCodec();
	}
}
