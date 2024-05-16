package ext.cadmigration.cadmass;

import java.util.Locale;
import com.ptc.core.lwc.server.PersistableAdapter;
import wt.fc.Persistable;
import wt.util.WTException;
public class PartUtil {

	public static Object getAttributeValue(Persistable per, String internalName)
	{
		Object value = null;
		PersistableAdapter obj;
		try {
			obj = new PersistableAdapter(per, null, Locale.US, null);
			obj.load(internalName);
			if (obj.get(internalName) != null)
			{
				value = obj.get(internalName);
			}
		} catch (WTException e) {
			e.printStackTrace();
			return value;
		}

		finally {
			per = null;
			obj = null;
		}

		return value;
	}
}