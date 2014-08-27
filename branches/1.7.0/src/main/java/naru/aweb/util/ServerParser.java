package naru.aweb.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;

/**
 * host:port�`���̕����������
 * host�ɂ́A"*"�̃��C���h�J�[�h���w��\
 * ����ServerParser�ƈ�v���邩�ǂ����́Amatches���\�b�h�Ŋm�F�ł���
 * 
 * @author Naru
 */
public class ServerParser extends PoolBase {
	public static final int HTTP_PORT_NUMBER = 80;
	public static final int HTTPS_PORT_NUMBER = 443;
	public static final int WILD_PORT_NUMBER=-1;
	private static Pattern serverPattern=Pattern.compile("^([^:\\s]*)(?::(\\d*))?");
	private static Pattern urlPattern=     Pattern.compile("^(?:([^:/?#]+):)?(?://([^/?#]*))?([^?#]*)(?:\\?([^#]*))?(?:#(.*))?");
	private static Pattern urlShortPattern=Pattern.compile("^(?:([^:/?#]+):)?(?://([^/?#]*))?(.*)?");
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
	
	private static boolean equalsSb(StringBuilder sb1,StringBuilder sb2){
		int len1=sb1.length();
		int len2=sb2.length();
		if(len1!=len2){
			return false;
		}
		for(int i=0;i<len1;i++){
			if( sb1.charAt(i)!=sb2.charAt(i) ){
				return false;
			}
		}
		return true;
	}
	
	public static boolean equalsUrl(String url1,String url2){
		if(url1.equals(url2)){
			return true;
		}
		StringBuilder scheme1=new StringBuilder();
		StringBuilder path1=new StringBuilder();
		StringBuilder scheme2=new StringBuilder();
		StringBuilder path2=new StringBuilder();
		ServerParser server1=parseUrl(url1,scheme1,path1);
		ServerParser server2=parseUrl(url2,scheme2,path2);
		try {
			if(!equalsSb(scheme1,scheme2)){
				return false;
			}
			if(!equalsSb(path1,path2)){
				return false;
			}
			if(!server1.equals(server2)){
				return false;
			}
			return true;
		} finally{
			server1.unref(true);
			server2.unref(true);
		}
	}

	public static ServerParser parseUrl(String url,StringBuilder schemeSb,StringBuilder pathSb){
		Matcher matcher=null;
		synchronized(urlShortPattern){
			matcher=urlShortPattern.matcher(url);
		}
		if(!matcher.matches()){
			return null;
		}
		String matchScheme=matcher.group(1);
		String matchAuthority=matcher.group(2);
		String matchPath=matcher.group(3);
		
		int defaultPort=WILD_PORT_NUMBER;
		if("http".equalsIgnoreCase(matchScheme)||"ws".equalsIgnoreCase(matchScheme)){
			defaultPort=HTTP_PORT_NUMBER;
		}else if("https".equalsIgnoreCase(matchScheme)||"wss".equalsIgnoreCase(matchScheme)){
			defaultPort=HTTPS_PORT_NUMBER;
		}
		ServerParser server=parse(matchAuthority,defaultPort);
		if(schemeSb!=null){
			schemeSb.append(matchScheme);
		}
		if(pathSb!=null){
			pathSb.append(matchPath);
		}
		return server;
	}
	
	public static ServerParser parseUrl(String url,StringBuilder schemeSb,StringBuilder pathSb,StringBuilder querySb,StringBuilder fragmentSb){
		Matcher matcher=null;
		synchronized(urlPattern){
			matcher=urlPattern.matcher(url);
		}
		if(!matcher.matches()){
			return null;
		}
		String matchScheme=matcher.group(1);
		String matchAuthority=matcher.group(2);
		String matchPath=matcher.group(3);
		String matchQuery=matcher.group(4);
		String matchFragment=matcher.group(5);
		
		int defaultPort=WILD_PORT_NUMBER;
		if("http".equalsIgnoreCase(matchScheme)||"ws".equalsIgnoreCase(matchScheme)){
			defaultPort=HTTP_PORT_NUMBER;
		}else if("https".equalsIgnoreCase(matchScheme)||"wss".equalsIgnoreCase(matchScheme)){
			defaultPort=HTTPS_PORT_NUMBER;
		}
		ServerParser server=parse(matchAuthority,defaultPort);
		if(schemeSb!=null){
			schemeSb.append(matchScheme);
		}
		if(pathSb!=null){
			pathSb.append(matchPath);
		}
		if(querySb!=null){
			querySb.append(matchQuery);
		}
		if(fragmentSb!=null){
			fragmentSb.append(matchFragment);
		}
		return server;
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
	 * �����̒���ServerParser���K�v�ȏꍇ�APool�Ǘ����Ȃ��B
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
			//�K��pool�ɂ���Ƃ͌���Ȃ�������pool����Ă���ꍇ�ɂ͌Ăяo�����̑���ɊJ������
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
	 * �����̒���ServerParser���K�v�ȏꍇ�APool�Ǘ����Ȃ��B
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
		if(host.startsWith("!")){//!�ŊJ�n���ꂽ�ꍇ�A�����K�\��
			host=host.substring(1);
			hostPattern=Pattern.compile(host);
			this.host=host;
		}else if(host.indexOf("*")>=0){//*���܂܂ꂽ�ꍇ�A���C���h�J�[�h�u�������Ƃ���
			String hostPtn=host.replaceAll("\\*", "(\\\\S*)");
			if(hostPtn.indexOf(".")>=0){
				hostPtn=hostPtn.replaceAll("\\.", "\\\\.");
			}
			hostPattern=Pattern.compile(hostPtn);
		}
	}
	
	public void setupPortIfNeed(boolean isSsl){
		if(port!=WILD_PORT_NUMBER){
			return;
		}
		if(isSsl){
			port=HTTPS_PORT_NUMBER;
		}else{
			port=HTTP_PORT_NUMBER;
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
	
	public Matcher hostMatcher(String targetHost){
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
