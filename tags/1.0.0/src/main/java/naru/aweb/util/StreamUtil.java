package naru.aweb.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;

public class StreamUtil {
	/**
	 * InputStream�ɂ���f�[�^����C�ɓǂݍ���
	 * ��Ƀe�X�g�p
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static byte[] readAll(InputStream is) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		while (true) {
			int len = is.read(buf);
			if (len < 0) {
				break;
			}
			baos.write(buf, 0, len);
		}
		is.close();
		return baos.toByteArray();
	}

	/**
	 * remote��in����������ŁA�o�͂��Ō�܂œǂݍ���ŕԋp����
	 * �e�X�g�p
	 * @param remote
	 * @param in
	 * @return
	 * @throws IOException
	 */
	public static byte[] socketIo(SocketAddress remote, byte[] in) throws IOException {
		Socket socket = new Socket();
		byte[] result;
		try {
			socket.connect(remote);
			OutputStream socketOut = socket.getOutputStream();
			socketOut.write(in);
			InputStream socketIn = socket.getInputStream();
			result = readAll(socketIn);
		} finally {
			socket.close();
		}
		return result;
	}

}
