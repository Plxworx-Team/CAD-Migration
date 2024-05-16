
package ext.cadmigration.cadmass;

import java.io.IOException;
import com.ptc.core.lwc.server.PersistableAdapter;
import com.ptc.core.meta.common.OperationIdentifier;
import com.ptc.core.meta.common.OperationIdentifierConstants;
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
import wt.util.WTException;
import wt.vc.VersionControlHelper;
import wt.vc.config.ConfigHelper;

public class MassCalculation implements RemoteAccess {

	public static void main(String[] argv) throws WTException, Exception {
		if (argv.length == 0) {
			System.out.println("Usage: windchill");
			System.exit(0);
		}

		String PartNumber = argv[0];
		RemoteMethodServer rms = RemoteMethodServer.getDefault();
		rms.setUserName("wcadmin");
		rms.setPassword("wcadmin");
		Class[] argTypes = { String.class };
		Object[] argValues = { PartNumber };
		rms.invoke("FindPart", MassCalculation.class.getName(), null, argTypes, argValues);
	}

	public static void FindPart(String PartNumber) throws WTException, IOException {

		try {
			QuerySpec querySpec = new QuerySpec(WTPartMaster.class);
			SearchCondition searchCondition = new SearchCondition(WTPartMaster.class, WTPartMaster.NUMBER,
					SearchCondition.EQUAL, PartNumber);

			querySpec.appendWhere(searchCondition, new int[] { 0, -1 });
			QueryResult qr = PersistenceHelper.manager.find(querySpec);
			if (qr.hasMoreElements()) {
				WTPartMaster partMaster = (WTPartMaster) qr.nextElement();
				System.out.println("Part Name is: " + partMaster.getName());

				// Get parent part
				WTPart parentPart = getParent(partMaster);
				QuerySpec querySpecMasters = new QuerySpec(WTPartMaster.class);
				Class qcDoc = WTPartMaster.class;
				int idxP = querySpecMasters.addClassList(qcDoc, true);
				querySpecMasters.appendWhere(
						new SearchCondition(WTPartMaster.class, WTPartMaster.NUMBER, SearchCondition.EQUAL, PartNumber),
						new int[] { idxP });

				// Get Iteration and attribute value of LatestVersion
				QueryResult qrPart = PersistenceHelper.manager.find(querySpecMasters);
				QueryResult qrlatest = ConfigHelper.service.filteredIterationsOf(qrPart,
						new wt.vc.config.LatestConfigSpec());
				while (qrlatest.hasMoreElements()) {
					WTPart latestPart = (WTPart) qrlatest.nextElement();
					System.out.println("Latest Part Name and Version : " + latestPart.getName() + " --- "
							+ latestPart.getIterationDisplayIdentifier());
					FloatingPointWithUnits latestAttributeValue = (FloatingPointWithUnits) PartUtil
							.getAttributeValue(latestPart, "CAD_Mass");
					System.out.println("Latest attributeValue: " + latestAttributeValue);
					WTPartMaster ChildPart = getChild(latestPart);

					// Get iteration and attribute value of PreviousVersion
					WTPart previousPart = (WTPart) VersionControlHelper.service.predecessorOf(latestPart);
					System.out.println("Previous Part Name and Version : " + previousPart.getName() + " --- "
							+ previousPart.getIterationDisplayIdentifier());
					FloatingPointWithUnits previousAttributeValue = (FloatingPointWithUnits) PartUtil
							.getAttributeValue(previousPart, "CAD_Mass");
					System.out.println("Previous attributeValue: " + previousAttributeValue);

					// Check if the attribute values are equal
					if (latestAttributeValue != null && latestAttributeValue.equals(previousAttributeValue)) {
						System.out.println("Attribute values are the same: " + latestAttributeValue);
					} else {
						// Perform subtraction if attribute values are different
						System.out.println("Attribute values are Different.... ");
						if (latestAttributeValue != null && previousAttributeValue != null) {
							try {

								// Extract numerical values
								double latestValue = latestAttributeValue.getValue();
								double previousValue = previousAttributeValue.getValue();

								// Check which value is greater
								double difference = latestValue - previousValue;
								System.out.println("difference.... " + difference);

								// Update attribute with the difference
								updateAttribute(parentPart, difference);

							} catch (Exception e) {
								// Handle any exceptions
								e.printStackTrace();
							}

						} else if (latestAttributeValue != null && previousAttributeValue == null) {
							try {
								double latestValue = latestAttributeValue.getValue();
								double difference = latestValue;

								// Update attribute with the difference
								updateAttribute(parentPart, difference);
							} catch (Exception e) {

								// Handle any exceptions
								e.printStackTrace();

							}
						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Persistable updateAttribute(WTPart part, double difference) throws WTException {
		PersistableAdapter persistableAdapter = new PersistableAdapter(part, null, SessionHelper.getLocale(),
				OperationIdentifier.newOperationIdentifier(OperationIdentifierConstants.UPDATE));
		persistableAdapter.load("CAD_Mass");

		// To get FloatingPointWithUnits value
		FloatingPointWithUnits existingValue = (FloatingPointWithUnits) persistableAdapter.get("CAD_Mass");
		System.out.println("existingValue: " + existingValue);
		FloatingPointWithUnits Differntvalue = new FloatingPointWithUnits(new Unit(difference, "m/s**2"));
		System.out.println("newVal: " + Differntvalue);
		FloatingPointWithUnits combined = FloatingPointWithUnits.add(existingValue, Differntvalue);
		System.out.println("combined: " + combined);
		// to set new FloatingPointWithUnits value

		// Update attribute value for the current part
		persistableAdapter.set("CAD_Mass", combined);
		Persistable persistable = persistableAdapter.apply();
		PersistenceServerHelper.manager.update(persistable);
		AttributeContainer attributeContainer = new IBAValueDBService()
				.updateAttributeContainer((IBAHolder) persistable, persistableAdapter, null, null);
		((IBAHolder) persistable).setAttributeContainer(attributeContainer);
		System.out.println("Attribute Values updated Successfully");

		// Check if the part has a parent
		WTPartMaster parentPartMaster = part.getMaster();
		QueryResult qrparent = WTPartHelper.service.getUsedByWTParts(parentPartMaster);
		while (qrparent.hasMoreElements()) {
			WTPart parentPart = (WTPart) qrparent.nextElement();
			System.out.println("The parent of the part.: " + parentPart.getName());

			// Recursively update the attribute value for each parent
			updateAttribute(parentPart, difference);

		}

		return part;

	}

	public static WTPart getParent(WTPartMaster partMaster) throws WTException {

		// Get parent of part
		QueryResult qrparent = WTPartHelper.service.getUsedByWTParts(partMaster);
		if (!qrparent.hasMoreElements()) {
			System.out.println("Parent part not found.");

		} else {
			// If parent part is found, iterate over the elements
			while (qrparent.hasMoreElements()) {
				WTPart parentpart = (WTPart) qrparent.nextElement();
				System.out.println("The parent of the part.: " + parentpart.getName());
				return parentpart;
			}
		}

		return null;
	}

	public static WTPartMaster getChild(WTPart part) throws WTException {

		// Get Child part of latest iteration
		QueryResult qrchild = WTPartHelper.service.getUsesWTPartMasters(part);
		while (qrchild.hasMoreElements()) {
			WTPartUsageLink ul = (WTPartUsageLink) qrchild.nextElement();
			WTPartMaster childPart = (WTPartMaster) ul.getUses();
			System.out.println("The children of the part.: " + childPart.getName());
			return childPart;
		}
		return null;

	}
}