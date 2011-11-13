package naru.aweb.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;

/**
 * host:port形式の文字列を解釈
 * hostには、"*"のワイルドカードが指定可能
 * 他のServerParserと一致するかどうかは、matchesメソッドで確認できる
 * 
 * @author Naru
 */
public class ServerParser extends PoolBase {
	public static final int HTTP_PORT_NUMBER = 80;
	public static final int HTTPS_PORT_NUMBER = 443;
	public static final int WILD_PORT_NUMBER=-1;
	private static Pattern serverPattern=Pattern.compile("^([^:\\s]*)(?::(\\d*))?");
	private static Set localhosts;
	static{
		localhosts=new HashSet();
		localhosts.add("localhsot");
		localhosts.add("127.0.0.1");
	}
	
	public static boolean isLocalhost(ServerParser server){
		return localhosts.contains(server.host);
	}
	
	public static void resolveLocalhost(ServerParser server,String remoteHost){
		if(isLocalhost(server)){
			server.host=remoteHost;
		}
	}
	
	public static ServerParser parse(String server){
		return parse(server,80);
	}
	
	public static ServerParser parse(String server,int defaultPort){
		if(server==null){
			return null;
		}
		ServerParser serverParser=(ServerParser)PoolManager.getInstance(ServerParser.class);
		return parse(serverParser,server,defaultPort);
	}
	
	/**
	 * 寿命の長いServerParserが必要な場合、Pool管理しない。
	 * @param serverParser
	 * @param server
	 * @param defaultPort
	 * @return
	 */
	public static ServerParser parse(ServerParser serverParser,String server,int defaultPort){
		serverParser.port=defaultPort;
		if("".equals(server)||server==null){
			return serverParser;
		}
		Matcher matcher=null;
		synchronized(serverPattern){
			matcher=serverPattern.matcher(server);
		}
		if(!matcher.matches()){
			//必ずpoolにあるとは限らないがもしpoolされている場合には呼び出し元の代わりに開放する
			serverParser.unref();
			return null;
		}
		serverParser.setHost(matcher.group(1));
		String portString=matcher.group(2);
		if(portString!=null){
			serverParser.port=Integer.parseInt(portString);
		}
		return serverParser;
	}
	public ServerParser(){
	}
	/**
	 * 寿命の長いServerParserが必要な場合、Pool管理しない。
	 * @param host
	 * @param port
	 */
	public ServerParser(String host,int port){
		this.setHost(host);
		this.port=port;
	}
	
	public static ServerParser create(String host,int port){
		ServerParser serverParser=(ServerParser)PoolManager.getInstance(ServerParser.class);
		serverParser.setHost(host);
		serverParser.port=port;
		return serverParser;
	}
	
	private String host=null;
	private Pattern hostPattern;
	private int port=WILD_PORT_NUMBER;
	
	private void setHost(String host){
		this.host=host;
		if(host==null){
			return;
		}
		if(host.startsWith("!")){//!で開始された場合、生正規表現
			host=host.substring(1);
			hostPattern=Pattern.compile(host);
			this.host=host;
		}else if(host.indexOf("*")>=0){//*が含まれた場合、ワイルドカード置換文字とする
			String hostPtn=host.replaceAll("\\*", "(\\\\S*)");
			if(hostPtn.indexOf(".")>=0){
				hostPtn=hostPtn.replaceAll("\\.", "\\\\.");
			}
			hostPattern=Pattern.compile(hostPtn);
		}
	}
	
	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public boolean isWildHost(){
		return hostPattern!=null;
	}
	
	public Matcher matcherHost(String targetHost){
		Matcher matcher=null;
		synchronized(hostPattern){
			matcher=hostPattern.matcher(targetHost);
		}
		if(!matcher.matches()){
			return null;
		}
		return matcher;
	}
	
	
	@Override
	public void recycle() {
		host=null;
		port=-1;
		hostPattern=null;
		super.recycle();
	}
	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof ServerParser)){
			return false;
		}
		ServerParser sp=(ServerParser)obj;
		if(port!=sp.port){
			return false;
		}
		if(host==null){
			if(sp.host!=null){
				return false;
			}
			return true;
		}
		if(!host.equals(sp.host)){
			return false;
		}
		return true;
	}
	@Override
	public int hashCode() {
		if(host==null){
			return 0;
		}
		return host.hashCode()+port;
	}

	@Override
	public String toString() {
		if(port==WILD_PORT_NUMBER){
			return host;
		}else{
			return host +":" +port;
		}
	}
	
	public String toServerString() {
		return toServerString(false);
	}
	
	public String toServerString(boolean isSsl) {
		if(port!=-1){
			return host +":" +port;
		}
		if(isSsl){
			return host +":443";
		}else{
			return host +":80";
		}
	}

	public int compareTo(ServerParser server) {
		int result=host.compareTo(server.host);
		if(result!=0){
			return result;
		}
		result=port-server.port;
		return result;
	}
}
