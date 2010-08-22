package com.dysoft.chatter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * @author Sean Micklethwaite
 *         Aug 14, 2010 6:58:47 PM
 */
public abstract class Manager implements Runnable {
	final Selector selector;
	final Stack<ByteBuffer> bufferPool = new Stack<ByteBuffer>();
	final Queue<Callable> tasks = new ArrayDeque<Callable>();

	Thread thread = null;


	protected Manager() throws IOException {
		selector = SelectorProvider.provider().openSelector();
		thread = new Thread(this, getClass().getName());
		thread.start();
		bufferPool.add(ByteBuffer.allocate(1024));
	}

	public void listen(String address, int port, final Dispatcher dispatcher) throws IOException {
		InetSocketAddress isa = new InetSocketAddress(address, port);

		final ServerSocketChannel channel = ServerSocketChannel.open();
		channel.configureBlocking(false);
		channel.socket().bind(isa);

		synchronized (tasks) {
			tasks.add(new Callable() {
				public Object call() throws Exception {
					channel.register(selector, SelectionKey.OP_ACCEPT, dispatcher);
					return null;
				}
			});
		}
		selector.wakeup();
	}

	/**
	 * Registers a socket for reading. Data will be sent to the passed transport session.
	 */
	protected <T extends SelectableChannel & ByteChannel> SocketTransport register(final T channel, final TransportSession session) throws IOException {
		final SocketTransport transport = new SocketTransport(channel);
		channel.configureBlocking(false);
		synchronized (tasks) {
			tasks.add(new Callable() {
				public Object call() throws Exception {
					channel.register(selector, SelectionKey.OP_READ, new SocketData(transport, session));
					return null;
				}
			});
		}
		selector.wakeup();
		return transport;
	}

	public void run() {
		do {
			try {
				synchronized (tasks) {
					for(Callable task : tasks) {
						try {
							task.call();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					tasks.clear();
				}
				selector.select();

				Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
				while(keys.hasNext()) {
					SelectionKey key = keys.next();
					keys.remove();

					if(!key.isValid()) continue;

					if(key.isAcceptable()) {
						ServerSocketChannel server = (ServerSocketChannel)key.channel();
						Dispatcher dispatcher = (Dispatcher) key.attachment();

						final SocketChannel channel = server.accept();
						final SocketTransport transport = new SocketTransport(channel);
						channel.configureBlocking(false);
						channel.register(selector, SelectionKey.OP_READ, new SocketData(
								transport, dispatcher.accept(transport)));
					} else if(key.isReadable()) {
						ByteChannel channel = (ByteChannel) key.channel();
						TransportSession transport = ((SocketData) key.attachment()).session;

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
						} finally {
							bufferPool.push(buffer);
						}
					} else if(key.isWritable()) {
						SocketTransport transport = ((SocketData) key.attachment()).transport;
						transport.processWrites(key);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} while(thread != null);
	}

	public interface Dispatcher {
		TransportSession accept(Transport channel);
	}

	class SocketTransport<T extends SelectableChannel & ByteChannel> implements Transport {
		final T channel;
		final Queue<ByteBuffer> sendQueue = new ArrayDeque<ByteBuffer>();

		SocketTransport(T channel) {
			this.channel = channel;
		}

		/**
		 * Registers write interest with the selector.
		 * @param data  Data to write
		 * @throws IOException
		 */
		public synchronized void send(ByteBuffer data) throws IOException {
			sendQueue.add(data);
			synchronized (tasks) {
				tasks.add(new Callable() {
					public Object call() throws Exception {
						channel.keyFor(selector).interestOps(SelectionKey.OP_WRITE);
						return null;
					}
				});
			}
			selector.wakeup();
		}

		public void close() throws IOException {
			synchronized (tasks) {
				tasks.add(new Callable() {
					public Object call() throws Exception {
						channel.keyFor(selector).interestOps(0);
						channel.close();
						return null;
					}
				});
			}
			selector.wakeup();
		}

		/**
		 * Works down the send queue, sending data until there is either no more, or
		 * the socket is full.
		 * @param key
		 * @throws IOException
		 */
		protected synchronized void processWrites(SelectionKey key) throws IOException {
			while(!sendQueue.isEmpty()) {
				ByteBuffer buf = sendQueue.peek();
				channel.write(buf);
				if(buf.hasRemaining()) {
					break; // Socket full
				} else {
					sendQueue.remove();
				}
			}
			if(sendQueue.isEmpty()) {
				key.interestOps(SelectionKey.OP_READ);
			}
		}
	}

	static class SocketData {
		public final SocketTransport transport;
		public final TransportSession session;

		SocketData(SocketTransport transport, TransportSession session) {
			this.transport = transport;
			this.session = session;
		}
	}
}
