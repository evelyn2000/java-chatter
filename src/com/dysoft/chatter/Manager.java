package com.dysoft.chatter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collection;
import java.util.Iterator;
import java.util.Stack;

/**
 * @author Sean Micklethwaite
 *         Aug 14, 2010 6:58:47 PM
 */
public abstract class Manager implements Runnable {
	final Selector selector;
	final Stack<ByteBuffer> bufferPool = new Stack<ByteBuffer>();

	Thread thread = null;


	protected Manager() throws IOException {
		selector = SelectorProvider.provider().openSelector();
	}

	public void listen(String address, int port, Dispatcher dispatcher) throws IOException {
		InetSocketAddress isa = new InetSocketAddress(address, port);

		ServerSocketChannel channel = ServerSocketChannel.open();
		channel.configureBlocking(false);
		channel.socket().bind(isa);
		channel.register(selector, SelectionKey.OP_ACCEPT, dispatcher);
	}

	public void run() {
		do {
			try {
				selector.select();

				Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
				while(keys.hasNext()) {
					SelectionKey key = keys.next();
					keys.remove();

					if(!key.isValid()) continue;

					if(key.isAcceptable()) {
						ServerSocketChannel server = (ServerSocketChannel)key.channel();
						Dispatcher dispatcher = (Dispatcher) key.attachment();

						SocketChannel channel = server.accept();
						channel.configureBlocking(false);
						channel.register(selector, SelectionKey.OP_READ, dispatcher.accept(channel));
					} else if(key.isReadable()) {
						SocketChannel channel = (SocketChannel) key.channel();
						Transport transport = (Transport) key.attachment();

						ByteBuffer buffer = bufferPool.pop();
						buffer.clear();
						try {
							int numRead = channel.read(buffer);
							if(numRead < 0) {
								key.channel().close();
								key.cancel();
								transport.onClose();
							} else {
								transport.onReceive(buffer);
							}
						} catch (IOException e) {
							key.cancel();
							channel.close();
							transport.onClose();
						}
						bufferPool.push(buffer);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} while(thread != null);
	}

	public void runThread() {
		thread = new Thread(this, getClass().getName());
		thread.run();
	}

	public interface Dispatcher {
		Transport accept(SocketChannel channel);
	}
}
