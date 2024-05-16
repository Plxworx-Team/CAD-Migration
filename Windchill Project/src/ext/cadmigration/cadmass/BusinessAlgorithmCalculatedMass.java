package ext.cadmigration.cadmass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.ptc.core.businessfield.common.BusinessField;
import com.ptc.core.businessfield.common.BusinessFieldIdFactoryHelper;
import com.ptc.core.businessfield.common.BusinessFieldServiceHelper;
import com.ptc.core.businessfield.server.BusinessFieldIdentifier;
import com.ptc.core.businessfield.server.businessObject.BusinessAlgorithm;
import com.ptc.core.businessfield.server.businessObject.BusinessAlgorithmContext;
import com.ptc.core.businessfield.server.businessObject.BusinessObject;
import com.ptc.core.businessfield.server.businessObject.BusinessObjectHelper;
import com.ptc.core.businessfield.server.businessObject.BusinessObjectHelperFactory;
import com.ptc.core.meta.common.DisplayOperationIdentifier;
import com.ptc.core.meta.common.TypeIdentifier;

import wt.clients.beans.explorer.WTList;
import wt.fc.Persistable;
import wt.fc.WTReference;
import wt.fc.collections.CollectionsHelper;
import wt.fc.collections.WTArrayList;
import wt.fc.collections.WTCollection;
import wt.filter.NavigationCriteria;
import wt.navigation.PartRequest;
import wt.part.WTPart;
import wt.type.TypedUtility;
import wt.units.FloatingPointWithUnits;
import wt.units.Unit;
import wt.units.UnitFormatException;
import wt.util.WTContext;
import wt.util.WTException;
import wt.vc.config.LatestConfigSpec;
import wt.navigation.DTRequest;
import wt.navigation.DependencyHelper;
import wt.navigation.PartRequest;

public class BusinessAlgorithmCalculatedMass implements BusinessAlgorithm {

	// execute("ext.cadmigration.cadmass.BusinessAlgorithmCalculatedMass","CAD_Mass")
	private static final TypeIdentifier partType = TypedUtility.getTypeIdentifier(WTPart.class);
	private static final List<LatestConfigSpec> latestconfigspec = Collections
			.unmodifiableList(Arrays.asList(new LatestConfigSpec()));
	private static final DisplayOperationIdentifier displayope = new DisplayOperationIdentifier();
	private static final BusinessObjectHelper businessobj = BusinessObjectHelperFactory.getBusinessObjectHelper();

	@Override
	public Object execute(BusinessAlgorithmContext context, Object[] args) {
		Object retValue = null; // default value for this algorithm
		TypeIdentifier currentBusObjectType = null;

		if (context == null) {
			System.out.println("execute(): context was null.");
		} else if (args == null) {
			System.out.println("execute(): args[] was null.");
		} else {

			// get the attribute name to sum from the args array
			final String attributeNameToSum = getAttName(args);
			final BusinessObject currrentBusObject = context.getCurrentBusinessObject();
			try {
				if (currrentBusObject != null) {
					currentBusObjectType = currrentBusObject.getTypeIdentifier();
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				currentBusObjectType = null; // It would be null anyway at this point, but this is for clarity
			}
			if ((currentBusObjectType != null) && (currentBusObjectType.isDescendedFrom(partType))
					&& (attributeNameToSum != null)) {
				WTCollection collection = null;
				try {
					collection = getAllPartStructure(currrentBusObject);
				} catch (WTException e) {

					e.printStackTrace();
				}
				if (collection != null) {

					try {
						final BusinessField fieldToSum = getBusinessField(attributeNameToSum, currentBusObjectType);

						// call helper to get all of the values of the specified attribute over this
						// Part structure
						final List<Number> allValuesForAttribute = getPartStructureValues(collection, fieldToSum);

						// call appropriate "sum" method to calculate the sum of these values.
						retValue = calculateSumValue(allValuesForAttribute);

					} catch (Exception ex) {
						ex.printStackTrace();
						
					}
				}
			}
		}
		return retValue;
	}

	/**
	 * Given a root part, get all parts in its part structure at once.
	 *
	 * @param curBusinessObject
	 *            the current business object that is the root of the structure
	 * @return a WTCollection containing all of the parts in the structure
	 * @throws WTException
	 */
	private WTCollection getAllPartStructure(final BusinessObject curBusinessObject) throws WTException {
		if ((curBusinessObject != null) && (curBusinessObject.getWTReference() != null)) {
			// only add the object if it has a valid reference
			final WTArrayList busObjRefs = new WTArrayList(1);
			final WTReference rootPartToWalk = curBusinessObject.getWTReference();

			busObjRefs.add(rootPartToWalk);
			final NavigationCriteria criteria = NavigationCriteria.newNavigationCriteria();
			criteria.setConfigSpecs(latestconfigspec);
			final PartRequest request = new PartRequest(busObjRefs, criteria, DTRequest.Scope.PROJECT, -1,
					DTRequest.DependencyTracing.ALL);
			final DependencyHelper dependencyHelper = new DependencyHelper(request);
			final WTCollection collectedObjects = dependencyHelper.getCollectedObjects();
			System.out.println("collectedObjects= " + collectedObjects);
			return collectedObjects;
		} else {
			return CollectionsHelper.EMPTY_COLLECTION;
		}
	}

	private List<Number> getPartStructureValues(final WTCollection partCollection, final BusinessField fieldToSum)
			throws WTException {
		// create new list that contains all parts in this structure
		final Persistable[] allParts = partCollection.toArray(new Persistable[partCollection.size()]);

		final List<BusinessObject> busObjs = businessobj.newBusinessObjects(WTContext.getContext().getLocale(),
				displayope, false, allParts);
		businessobj.load(busObjs, Arrays.asList(fieldToSum));
		final List<Number> allValues = new ArrayList<>(allParts.length);
		for (BusinessObject busobj : busObjs) {
			final Object val = busobj.get(fieldToSum);
			if (val instanceof Number) {
				// single valued attribute
				allValues.add((Number) val);
			} else if (val instanceof Object[]) {
				// multi-valued (IBA) attribute
				final Object[] vals = (Object[]) val;
				for (Object aVal : vals) {
					if (aVal instanceof Number) {
						allValues.add((Number) aVal);
					} else {
						System.out.println("getPartStructureValues(): invalid value: " + aVal);
					}
				}
			} else {
				if (val != null) {
					System.out.println("getPartStructureValues(): invalid value: " + val);
				}
			}
		}
		return allValues;
	}

	/**
	 * Helper to get the attribute name from the args array
	 * 
	 * @param args
	 * @return attribute name if found, null if not
	 */

	private String getAttName(final Object[] args) {
		final String attributeName;
		if ((args == null) || (args.length < 1) || !(args[0] instanceof String)) {
			System.out.println("No args passed into execute() method in business algorithm!: " + this.getClass());
			attributeName = null;
		} else if (!(args[0] instanceof String)) {
			System.out.println("execute(): Wrong Datatype passed into execute() method in business algorithm: "
					+ args[0].getClass());
			attributeName = null;
		} else {
			attributeName = (String) args[0];
			System.out.println("Attribute name= " + attributeName);
		}
		return attributeName;
	}

	private static BusinessField getBusinessField(final String fieldName, final TypeIdentifier contextType)
			throws WTException {
		final BusinessFieldIdentifier fieldIdentifier = BusinessFieldIdFactoryHelper.FACTORY
				.getTypeBusinessFieldIdentifier(fieldName, contextType);
		return BusinessFieldServiceHelper.SERVICE.getBusinessField(fieldIdentifier);
	}

	public FloatingPointWithUnits calculateSumValue(List<Number> allValuesForAttribute) throws WTException {

		FloatingPointWithUnits runningTotalFPWU = null;
		// sum (add-up) all of the values
		for (Object anObj : allValuesForAttribute) {
			if (anObj instanceof FloatingPointWithUnits) {
				runningTotalFPWU = FloatingPointWithUnits.add(runningTotalFPWU, (FloatingPointWithUnits) anObj);
				System.out.println("FloatingPointWithUnits=" + runningTotalFPWU);
			}
		}
		// return appropriate "sum" value
		return runningTotalFPWU;
	}

	@Override
	public Object getSampleValue() {
		FloatingPointWithUnits result = null;
		Random randomGenerator = new Random();
		double randomDouble = randomGenerator.nextDouble() * Math.pow(10, randomGenerator.nextInt(4));
		String units = "m/s**2";
		int randomPrecision = randomGenerator.nextInt(10);
		try {
			result = new FloatingPointWithUnits(new Unit(randomDouble, randomPrecision, units));
			System.out.println("result= " + result);
		} catch (UnitFormatException ufe) {
			// just return null
		}

		return result;

	}

}
