package com.itahm;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Syslog extends SyslogServer implements Closeable {
	private final static SimpleDateFormat DIR_STRING = new SimpleDateFormat("yyyyMMdd");
	
	private final File root;
	private Map<String, File> map = new HashMap<>();
	private File dir;
	private int day;
	
	public Syslog(File dataRoot) throws IOException {
		Calendar c = Calendar.getInstance();
		
		root = dataRoot;
		dir = new File(root, DIR_STRING.format(c.getTime()));
		day = c.get(Calendar.DAY_OF_YEAR);
		
		dir.mkdir();
	}

	@Override
	public void onMessage(String ip, byte[] message) {
		Calendar c = Calendar.getInstance();
		int day = c.get(Calendar.DAY_OF_YEAR);
		File f;
		
		if (day != this.day) {
			this.day = day;
			this.dir = new File(root, DIR_STRING.format(c.getTime()));
			
			dir.mkdir();
		}
		
		f = this.map.get(ip);
		
		if (!new File (this.dir, ip).equals(f)) {
			f = new File (this.dir, ip);
			
			this.map.put(ip, f);
		}
		
		try(FileOutputStream fos = new FileOutputStream(f, true)) {
			fos.write(message);
			fos.write('\n');
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String getHost(long date) {
		File dir = new File(this.root, DIR_STRING.format(new Date(date)));
		String hosts = "";
		
		if (dir.isDirectory()) {
			for (File f : dir.listFiles()) {
				if (f.isFile()) {
					if (hosts.length() != 0) {
						hosts += ",";
					}
					
					hosts += f.getName();
				}
			}
		}
		
		return hosts;
	}
	
	public byte [] getLog(String ip, long date) throws IOException {
		File f = new File(this.root, DIR_STRING.format(new Date(date)));
		
		if (!f.isDirectory()) {
			return new byte[0];
		}
		
		f = new File(f, ip);
		
		if (!f.isFile()) {
			return new byte[0];
		}
		
		return Files.readAllBytes(f.toPath());
	}
	
	@Override
	public void close() throws IOException {
		super.close();
	}
}
