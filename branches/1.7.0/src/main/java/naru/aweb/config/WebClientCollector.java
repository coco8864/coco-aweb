package naru.aweb.config;

public class WebClientCollector {
	private class Collection{
		private int count=0;
		private long sum=0L;
		private long sumsq=0L;
	}
	
	private Collection readLengths[]=new Collection[WebClientLog.CHECK_POINT_NUM];
	private Collection writeLengths[]=new Collection[WebClientLog.CHECK_POINT_NUM];
	private Collection processTimes[]=new Collection[WebClientLog.CHECK_POINT_NUM];
	
	public WebClientCollector(){
		for(int i=0;i<WebClientLog.CHECK_POINT_NUM;i++){
			readLengths[i]=new Collection();
            writeLengths[i]=new Collection();
            processTimes[i]=new Collection();
		}
	}
	
	private void collect(long data,Collection collection){
		if(data<0){
			return;
		}
		collection.count++;
		collection.sum+=data;
		collection.sumsq+=(data*data);
	}
	
	public void add(WebClientLog webClientLog){
		for(int i=(WebClientLog.CHECK_POINT_START+1);i<WebClientLog.CHECK_POINT_END;i++){
			collect(webClientLog.getReadLength(i),readLengths[i]);
			collect(webClientLog.getWriteLength(i),writeLengths[i]);
			collect(webClientLog.getProcessTime(i),processTimes[i]);
		}
		collect(webClientLog.getReadLength(WebClientLog.CHECK_POINT_END)-
				webClientLog.getReadLength(WebClientLog.CHECK_POINT_START),
				readLengths[WebClientLog.CHECK_POINT_END]);
		collect(webClientLog.getWriteLength(WebClientLog.CHECK_POINT_END)-
				webClientLog.getWriteLength(WebClientLog.CHECK_POINT_START),
				writeLengths[WebClientLog.CHECK_POINT_END]);
		collect(webClientLog.getProcessTime(WebClientLog.CHECK_POINT_END)-
				webClientLog.getProcessTime(WebClientLog.CHECK_POINT_START),
				processTimes[WebClientLog.CHECK_POINT_END]);
	}
	
}
