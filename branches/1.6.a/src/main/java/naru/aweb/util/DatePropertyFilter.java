package naru.aweb.util;

import java.util.Date;

import net.sf.json.util.PropertyFilter;

public class DatePropertyFilter implements PropertyFilter {

	/* dateŒ^‚ğjson‚É‚·‚é‚Étime‘®«ˆÈŠO‚ğ–³‹‚·‚éİ’è */
	public boolean apply(Object source, String name, Object value) {
		if((source instanceof Date) && !name.equals("time")){
			return true;
		}
		return false;
	}
}
