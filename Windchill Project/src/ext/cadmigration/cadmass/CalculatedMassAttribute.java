
// CalculatedMassAttribute.java
 
  package ext.cadmigration.cadmass;
 
import com.ptc.core.lwc.server.PersistableAdapter;
import com.ptc.core.meta.common.OperationIdentifier;
import ext.cadmigration.cadmass.PartUtil;
import java.util.Locale;
import wt.fc.Persistable;
import wt.fc.PersistenceHelper;
import wt.fc.PersistenceServerHelper;
import wt.fc.QueryResult;
import wt.iba.value.AttributeContainer;
import wt.iba.value.IBAHolder;
import wt.iba.value.service.IBAValueDBService;
import wt.method.RemoteAccess;
import wt.method.RemoteMethodServer;
import wt.part.WTPart;
import wt.part.WTPartHelper;
import wt.part.WTPartMaster;
import wt.part.WTPartUsageLink;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.session.SessionHelper;
import wt.units.FloatingPointWithUnits;
import wt.units.Unit;
import wt.units.service.MeasurementSystemDefaultView;
import wt.util.WTException;
import wt.vc.VersionControlHelper;
import wt.vc.config.ConfigHelper;
import wt.vc.config.LatestConfigSpec;
 
public class CalculatedMassAttribute implements RemoteAccess {

   public static void main(String[] argv) throws Exception {
      if (argv.length == 0) {
         System.out.println("Usage: windchill");
         System.exit(0);
      }
 
      String partNumber = argv[0];
      RemoteMethodServer rms = RemoteMethodServer.getDefault();
      rms.setUserName("wcadmin");
      rms.setPassword("wcadmin");
      Class[] argTypes = new Class[]{String.class};
      Object[] argValues = new Object[]{partNumber};
      rms.invoke("findPart", CalculatedMassAttribute.class.getName(), (Object)null, argTypes, argValues);

   }
 
   public static void findPart(String partNumber) {
      try {
         QueryResult qr = findPartMaster(partNumber);
         if (qr.hasMoreElements()) {
            WTPartMaster partMaster = (WTPartMaster)qr.nextElement();
            System.out.println("Part Name is: " + partMaster.getName());
            QueryResult qrPart = findPartMaster(partNumber);
            QueryResult qrlatest = ConfigHelper.service.filteredIterationsOf(qrPart, new LatestConfigSpec());
            while(qrlatest.hasMoreElements()) {
               WTPart latestPart = (WTPart)qrlatest.nextElement();
               System.out.println("Latest Version of *" + latestPart.getName() + "*" + " is: " + latestPart.getIterationDisplayIdentifier());
               FloatingPointWithUnits latestAttributeValue = (FloatingPointWithUnits)PartUtil.getAttributeValue(latestPart, "CAD_Mass");
               System.out.println("Latest attributeValue: " + latestAttributeValue);
               getChildren(latestPart);
            }
         }

      } catch (Exception var7) {

         var7.printStackTrace();
      }
   }
 
   private static QueryResult findPartMaster(String partNumber) throws WTException {
      QuerySpec querySpec = new QuerySpec(WTPartMaster.class);
      querySpec.appendWhere(new SearchCondition(WTPartMaster.class, "number", "=", partNumber), new int[]{0, -1});
      return PersistenceHelper.manager.find(querySpec);
   }
 
   public static WTPartMaster getChildren(WTPart part) throws WTException {
      QueryResult qr = WTPartHelper.service.getUsesWTPartMasters(part);
      while(qr.hasMoreElements()) {
         WTPartUsageLink usageLink = (WTPartUsageLink)qr.nextElement();
         WTPartMaster child = usageLink.getUses();
         System.out.println("Name of Child is: " + child.getName());
         QueryResult qrPart = findPartMaster(child.getNumber()); 
         WTPart latestPart;
         for(QueryResult qrlatest = ConfigHelper.service.filteredIterationsOf(qrPart, new LatestConfigSpec()); qrlatest.hasMoreElements(); getChildren(latestPart)) {
            latestPart = (WTPart)qrlatest.nextElement();

            System.out.println("Latest Version of Child *" + latestPart.getName() + "*" + " is: " + latestPart.getIterationDisplayIdentifier());
            FloatingPointWithUnits latestAttributeValue = (FloatingPointWithUnits)PartUtil.getAttributeValue(latestPart, "CAD_Mass");
            System.out.println("Latest attributeValue of child: " + latestAttributeValue);
            WTPart previousPart = (WTPart)VersionControlHelper.service.predecessorOf(latestPart);
            if (previousPart == null) {
               System.out.println("No previous iteration found for: " + latestPart.getName());
            } else {
               System.out.println("Previous Version of Child  " + previousPart.getName() + " is: " + previousPart.getIterationDisplayIdentifier());
              FloatingPointWithUnits previousAttributeValue = (FloatingPointWithUnits)PartUtil.getAttributeValue(previousPart, "CAD_Mass");
               System.out.println("Previous attributeValue of Child: " + previousAttributeValue);
               if (latestAttributeValue != null && latestAttributeValue.equals(previousAttributeValue))
               {
                  System.out.println("Attribute values are the same: " + latestAttributeValue + " No need to update...");

               } else {
                  System.out.println("Attribute values are Different.... ");
                  double latestValue;
                  if (latestAttributeValue != null && previousAttributeValue != null) {
                     try {
                        latestValue = latestAttributeValue.getValue();
                        double previousValue = previousAttributeValue.getValue();
                        double difference = latestValue - previousValue;
                        System.out.println("difference.... " + difference);
                        getParent(child, difference);
                     } catch (Exception var17) {
                        var17.printStackTrace();
                     }

                  } else if (latestAttributeValue != null && previousAttributeValue == null) {
                     try {
                        latestValue = latestAttributeValue.getValue();
                        System.out.println("difference.... " + latestValue);
                        getParent(child, latestValue);
                     } catch (Exception var16) {
                        var16.printStackTrace();
                     }
                  }
               }
            }
         }
      }
 
      return null;

   }
 
   public static Persistable updateAttribute(WTPart part, double difference) throws WTException {
      PersistableAdapter persistableAdapter = new PersistableAdapter(part, (String)null, SessionHelper.getLocale(), OperationIdentifier.newOperationIdentifier("STDOP|com.ptc.windchill.update"));
      persistableAdapter.load(new String[]{"CAD_Mass"});
      FloatingPointWithUnits existingValue = (FloatingPointWithUnits)persistableAdapter.get("CAD_Mass");
      System.out.println("ExistingValue on  " + part.getName() + " is : " + existingValue);
      FloatingPointWithUnits differenceValue = new FloatingPointWithUnits(new Unit(difference, "m/s**2"));
      System.out.println("Value should be Added on Existing Attribute value: " + differenceValue);
      FloatingPointWithUnits updatedValue = FloatingPointWithUnits.add(existingValue, differenceValue);
      System.out.println("Updated Attribute Value will be: " + updatedValue);
      persistableAdapter.set("CAD_Mass", updatedValue);
      Persistable persistable = persistableAdapter.apply();
      PersistenceServerHelper.manager.update(persistable);
      AttributeContainer attributeContainer = (new IBAValueDBService()).updateAttributeContainer((IBAHolder)persistable, persistableAdapter, (Locale)null, (MeasurementSystemDefaultView)null);
      ((IBAHolder)persistable).setAttributeContainer(attributeContainer);
      System.out.println("Attribute Values updated Successfully");
      return part;

   }
 
   public static WTPart getParent(WTPartMaster partMaster, double difference) throws WTException {
      QueryResult qrparent = WTPartHelper.service.getUsedByWTParts(partMaster);
      System.out.println("Number of Parents of " + partMaster.getName() + ": " + qrparent.size());
      if (!qrparent.hasMoreElements()) {
         System.out.println("Parent part not found for part master: " + partMaster.getName());
         return null;
      } else {
         WTPart parentPart = (WTPart)qrparent.nextElement();
         System.out.println("Parent Name: " + parentPart.getName());
         updateAttribute(parentPart, difference);
         getParent(parentPart.getMaster(), difference);
         return parentPart;
      }
   }
}
