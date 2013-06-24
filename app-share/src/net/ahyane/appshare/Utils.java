package net.ahyane.appshare;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class Utils {
	public static int readInt(byte[] bytes){
		int ret = 0;
		ByteArrayInputStream bais = new ByteArrayInputStream(bytes, 0, 4);
		DataInputStream dis = new DataInputStream(bais);
		try {
			ret = dis.readInt();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {dis.close();} catch (IOException e) {}
			try {bais.close();} catch (IOException e) {}
		}
		return ret;
	}

	public static long readLong(byte[] bytes){
		long ret = 0;
		ByteArrayInputStream bais = new ByteArrayInputStream(bytes, 0, 8);
		DataInputStream dis = new DataInputStream(bais);
		try {
			ret = dis.readLong();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {dis.close();} catch (IOException e) {}
			try {bais.close();} catch (IOException e) {}
		}
		return ret;
	}
}
