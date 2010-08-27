package com.dysoft.chatter;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
	protected final static Logger LOG = Logger.getLogger(Manager.class);

	final Selector selector;
	final Stack<ByteBuffer> bufferPool = new Stack<ByteBuffer>();
	final Queue<Callable<Boolean>> priorityTasks = new ArrayDeque<Callable<Boolean>>();
	final Queue<Callable<Boolean>> tasks = new ArrayDeque<Callable<Boolean>>();

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
			priorityTasks.add(new Callable<Boolean>() {
				public Boolean call() throws Exception {
					channel.register(selector, SelectionKey.OP_ACCEPT, dispatcher);
					return true;
				}
			});
		}
		selector.wakeup();
	}

	public SocketTransport<SocketChannel> connect(InetSocketAddress isa, final Dispatcher dispatcher) throws IOException {
		final SocketChannel channel = SocketChannel.open(isa);
		channel.finishConnect();
		final SocketTransport<SocketChannel> transport = new SocketTransport<SocketChannel>(channel, isa);
		return register(transport, dispatcher.onConnect(transport));
	}

	/**
	 * Registers a socket for reading. Data will be sent to the passed transport session.
	 */
	protected <T extends SelectableChannel & ByteChannel> SocketTransport<T> register(final T channel, SocketAddress address, final TransportSession session) throws IOException {
		return register(new SocketTransport<T>(channel, address), session);
	}

	protected <T extends SelectableChannel & ByteChannel> SocketTransport<T> register(final SocketTransport<T> transport, final TransportSession session) throws IOException {
		transport.channel.configureBlocking(false);
		synchronized (tasks) {
			priorityTasks.add(new Callable<Boolean>() {
				public Boolean call() throws Exception {
					transport.channel.register(selector, SelectionKey.OP_READ, new SocketData(transport, session));
					return true;
				}
			});
		}
		selector.wakeup();
		return transport;
	}

	private void processTasks(final Queue<Callable<Boolean>> tasks) {
		try {
			synchronized (tasks) {
				while(!tasks.isEmpty()) {
					Callable<Boolean> start = tasks.peek();
					do {
						Callable<Boolean> task = tasks.remove();
						if(task.call()) {
							break;
						} else {
							tasks.add(task);
						}
					} while(!tasks.isEmpty() && start != tasks.peek());
					if(start == tasks.peek()) break;
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void run() {
		do {
			try {
				processTasks(priorityTasks);
				processTasks(tasks);

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
						final SocketTransport transport = new SocketTransport(channel, channel.socket().getRemoteSocketAddress());
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
								buffer.flip();
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
		TransportSession onConnect(Transport channel);
	}

	class SocketTransport<T extends SelectableChannel & ByteChannel> implements Transport {
		final T channel;
		final SocketAddress address;
		final Queue<ByteBuffer> sendQueue = new ArrayDeque<ByteBuffer>();

		SocketTransport(T channel, SocketAddress address) {
			this.channel = channel;
			this.address = address;
		}

		/**
		 * Registers write interest with the selector.
		 * @param data  Data to write
		 * @throws IOException
		 */
		public synchronized void send(ByteBuffer data) throws IOException {
			sendQueue.add(data);
			synchronized (tasks) {
				tasks.add(new Callable<Boolean>() {
					public Boolean call() throws Exception {
						if(!channel.isRegistered()) return false;
						channel.keyFor(selector).interestOps(SelectionKey.OP_WRITE);
						return true;
					}
				});
			}
			selector.wakeup();
		}

		public void close() throws IOException {
			synchronized (tasks) {
				priorityTasks.add(new Callable<Boolean>() {
					public Boolean call() throws Exception {
						if(channel.isRegistered()) {
							channel.keyFor(selector).interestOps(0);
						}
						channel.close();
						return true;
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
				LOG.debug("Writing " + buf);
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

		public SocketAddress getSocketAddress() {
			return address;
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
