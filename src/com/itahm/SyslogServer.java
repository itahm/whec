package com.itahm;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;

abstract public class SyslogServer implements Runnable, Closeable {

	private final int BUF_SIZE = 0xffff;
	
	private final DatagramChannel channel;
	private final Thread thread;
	
	public  SyslogServer() throws IOException {
		channel = DatagramChannel.open();
		channel.bind(new InetSocketAddress(514));
		
		thread = new Thread(this);
		thread.setName("ITAhM Syslog server");
		thread.start();
	}

	@Override
	public void run() {
		ByteBuffer buffer = ByteBuffer.allocateDirect(BUF_SIZE);
		SocketAddress peer;
		String ip;
		byte [] ba;
		
		while (!Thread.interrupted()) {
			try {
				peer = channel.receive(buffer);
				
				ip = peer instanceof InetSocketAddress? ((InetSocketAddress)peer).getAddress().getHostAddress(): peer.toString();
				
				buffer.flip();
				
				ba = new byte [buffer.remaining()];
				
				buffer.get(ba);
				
				this.onMessage(ip, ba);
				
				if (buffer.position() == buffer.capacity()) {
					buffer = ByteBuffer.allocateDirect(buffer.capacity() *0xf);
				}
				else {
					buffer.clear();
				}
			}
			catch (AsynchronousCloseException ace) {
				break;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void close() throws IOException {
		try {
			this.channel.close();
		}
		finally {
			try {
				this.thread.join();
			} catch (InterruptedException ie) {
			}
		}
	}
	
	abstract public void onMessage(String ip, byte [] message);
}
