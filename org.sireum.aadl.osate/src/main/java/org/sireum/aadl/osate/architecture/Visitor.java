package org.sireum.aadl.osate.architecture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.osate.aadl2.Aadl2Package;
import org.osate.aadl2.AbstractNamedValue;
import org.osate.aadl2.AccessConnection;
import org.osate.aadl2.AccessType;
import org.osate.aadl2.BooleanLiteral;
import org.osate.aadl2.Classifier;
import org.osate.aadl2.ClassifierValue;
import org.osate.aadl2.ConnectedElement;
import org.osate.aadl2.Connection;
import org.osate.aadl2.ConnectionEnd;
import org.osate.aadl2.DataClassifier;
import org.osate.aadl2.DataImplementation;
import org.osate.aadl2.DataSubcomponent;
import org.osate.aadl2.DirectionType;
import org.osate.aadl2.Element;
import org.osate.aadl2.EnumerationLiteral;
import org.osate.aadl2.Feature;
import org.osate.aadl2.FeatureConnection;
import org.osate.aadl2.FeatureGroupConnection;
import org.osate.aadl2.FeatureGroupPrototype;
import org.osate.aadl2.FeatureGroupType;
import org.osate.aadl2.ListValue;
import org.osate.aadl2.NamedElement;
import org.osate.aadl2.NamedValue;
import org.osate.aadl2.NumberValue;
import org.osate.aadl2.ParameterConnection;
import org.osate.aadl2.PortConnection;
import org.osate.aadl2.Property;
import org.osate.aadl2.PropertyAssociation;
import org.osate.aadl2.PropertyConstant;
import org.osate.aadl2.PropertyExpression;
import org.osate.aadl2.RangeValue;
import org.osate.aadl2.RecordValue;
import org.osate.aadl2.ReferenceValue;
import org.osate.aadl2.StringLiteral;
import org.osate.aadl2.Subcomponent;
import org.osate.aadl2.UnitLiteral;
import org.osate.aadl2.impl.AccessImpl;
import org.osate.aadl2.impl.BusAccessImpl;
import org.osate.aadl2.impl.BusSubcomponentImpl;
import org.osate.aadl2.impl.DataImplementationImpl;
import org.osate.aadl2.impl.DataPortImpl;
import org.osate.aadl2.impl.DataTypeImpl;
import org.osate.aadl2.impl.DirectedFeatureImpl;
import org.osate.aadl2.impl.EventDataPortImpl;
import org.osate.aadl2.impl.FeatureGroupImpl;
import org.osate.aadl2.instance.ComponentInstance;
import org.osate.aadl2.instance.ConnectionInstance;
import org.osate.aadl2.instance.ConnectionReference;
import org.osate.aadl2.instance.FeatureInstance;
import org.osate.aadl2.instance.FlowSpecificationInstance;
import org.osate.aadl2.instance.InstancePackage;
import org.osate.aadl2.instance.InstanceReferenceValue;
import org.osate.aadl2.instance.impl.FeatureInstanceImpl;
import org.osate.aadl2.modelsupport.util.AadlUtil;
import org.osate.aadl2.modelsupport.util.ResolvePrototypeUtil;
import org.osate.xtext.aadl2.properties.util.PropertyUtils;
import org.sireum.Option;
import org.sireum.Some;
import org.sireum.aadl.ir.AadlASTJavaFactory;

public class Visitor {

	final org.sireum.aadl.ir.AadlASTFactory factory = new org.sireum.aadl.ir.AadlASTFactory();

	final Map<String, org.sireum.aadl.ir.Component> datamap = new LinkedHashMap<>();
	final Map<List<String>, Set<Connection>> compConnMap = new HashMap<>();

	public Option<org.sireum.aadl.ir.Aadl> convert(Element root, boolean includeDataComponents) {
		final Option<org.sireum.aadl.ir.Component> t = visit(root);
		if(t.nonEmpty()) {
			final List<org.sireum.aadl.ir.Component> dataComponents =
					includeDataComponents ? new ArrayList<>(datamap.values()) : iList();
			return new Some<org.sireum.aadl.ir.Aadl>(
					factory.aadl(toIList(t.get()),
							iList(), //errorLib // FIXME
							dataComponents));
		} else {
			return org.sireum.None.apply();
		}
	}

	private Option<org.sireum.aadl.ir.Component> visit(Element root) {
		switch (root.eClass().getClassifierID()) {
		case InstancePackage.SYSTEM_INSTANCE:
		case InstancePackage.COMPONENT_INSTANCE: {
			org.sireum.aadl.ir.Component c = buildComponent((ComponentInstance) root, iList());
			return new Some<org.sireum.aadl.ir.Component>(c);
		}
		default:
			return org.sireum.None.apply();
		}
	}

	private List<org.sireum.aadl.ir.Connection> buildConnection(Connection conn, List<String> path,
			ComponentInstance compInst) {
		final List<String> name = add(path, conn.getName());
		final List<org.sireum.aadl.ir.EndPoint> src = buildEndPoint(conn.getSource(), path);
		final List<org.sireum.aadl.ir.EndPoint> dst = buildEndPoint(conn.getDestination(), path);
		final boolean isBiDirectional = conn.isBidirectional();
		final List<ConnectionInstance> connInst = compInst.findConnectionInstance(conn);

		List<org.sireum.aadl.ir.Name> connectionInstances = iList();
		if (!connInst.isEmpty()) {
			connectionInstances = connInst.stream()
					.map(ci -> factory.name(Arrays.asList(ci.getInstanceObjectPath().split("\\."))))
					.collect(Collectors.toList());
		}

		AadlASTJavaFactory.ConnectionKind kind = null;
		if (conn instanceof AccessConnection) {
			kind = AadlASTJavaFactory.ConnectionKind.Access;
		} else if (conn instanceof FeatureGroupConnection) {
			kind = AadlASTJavaFactory.ConnectionKind.FeatureGroup;
		} else if (conn instanceof FeatureConnection) {
			kind = AadlASTJavaFactory.ConnectionKind.Feature;
		} else if (conn instanceof ParameterConnection) {
			kind = AadlASTJavaFactory.ConnectionKind.Parameter;
		} else if (conn instanceof PortConnection) {
			kind = AadlASTJavaFactory.ConnectionKind.Port;
		} else {
			throw new RuntimeException("Unexpected connection kind: " + conn);
		}

		final List<org.sireum.aadl.ir.Property> properties = conn.getOwnedPropertyAssociations().stream()
				.map(pa -> buildProperty(pa, name)).collect(Collectors.toList());

		if (src.size() != dst.size()) {
			throw new RuntimeException("Incorrect translation!");
		}

		return toIList(factory.connection(factory.name(name), src, dst, kind, isBiDirectional, connectionInstances,
				properties));
	}

	private List<org.sireum.aadl.ir.EndPoint> buildEndPoint(ConnectedElement connElem, List<String> path) {
		List<org.sireum.aadl.ir.EndPoint> result = iList();
		final List<String> component = connElem.getContext() != null ? add(path, connElem.getContext().getName())
				: path;
		final List<String> feature = add(component, connElem.getConnectionEnd().getName());
		AadlASTJavaFactory.Direction dir = null;
		if (connElem.getConnectionEnd() instanceof DirectedFeatureImpl) {
			final DirectedFeatureImpl inFeature = (DirectedFeatureImpl) connElem.getConnectionEnd();
			if (inFeature.isIn() && inFeature.isOut()) {
				dir = AadlASTJavaFactory.Direction.InOut;
			} else if (inFeature.isIn() && !inFeature.isOut()) {
				dir = AadlASTJavaFactory.Direction.In;
			} else {
				dir = AadlASTJavaFactory.Direction.Out;
			}
		}
		final ConnectionEnd ce = connElem.getConnectionEnd();
		if (ce instanceof FeatureGroupImpl) {
			final FeatureGroupImpl fgce = (FeatureGroupImpl) ce;
			result = addAll(result, flattenFeatureGroup(component, fgce.getFullName(), fgce, connElem));
		} else if (ce instanceof BusSubcomponentImpl) {
			result = add(result, factory.endPoint(factory.name(feature), null, AadlASTJavaFactory.Direction.InOut));
		} else if (ce instanceof BusAccessImpl) {
			result = add(result, factory.endPoint(factory.name(component), factory.name(feature),
					AadlASTJavaFactory.Direction.InOut));
		} else {
			result = add(result, factory.endPoint(factory.name(component), factory.name(feature), dir));
		}
		return result;
	}

	private List<org.sireum.aadl.ir.EndPoint> flattenFeatureGroup(List<String> component, String parentName,
			FeatureGroupImpl fgi, ConnectedElement connElem) {
		List<org.sireum.aadl.ir.EndPoint> res = iList();
		FeatureGroupType fgt = fgi.basicGetFeatureGroupType();
		if (fgt == null) {
			final FeatureGroupPrototype fgpt = fgi.basicGetFeatureGroupPrototype();
			fgt = ResolvePrototypeUtil.resolveFeatureGroupPrototype(fgpt,
					connElem.getContext() == null
					? connElem.getContainingComponentImpl()
							: connElem.getContext());
		}
		for (Feature f : fgt.getAllFeatures()) {
			Feature rf = f.getRefined();
			if (rf == null) {
				rf = f;
			}
			if (rf instanceof FeatureGroupImpl) {
				res = addAll(res, flattenFeatureGroup(component, parentName + "_" + rf.getFullName(),
						(FeatureGroupImpl) rf, connElem));
			} else {
				AadlASTJavaFactory.Direction dir = null;
				if (AadlUtil.isIncomingFeature(rf) && AadlUtil.isOutgoingFeature(rf)) {
					dir = AadlASTJavaFactory.Direction.InOut;
				} else if (AadlUtil.isIncomingFeature(rf)) {
					dir = fgi.isInverse() ? AadlASTJavaFactory.Direction.Out : AadlASTJavaFactory.Direction.In;
				} else {
					dir = fgi.isInverse() ? AadlASTJavaFactory.Direction.In : AadlASTJavaFactory.Direction.Out;
				}

				res = add(res, factory.endPoint(factory.name(component),
						factory.name(add(component, parentName + "_" + rf.getFullName())), dir));
			}
		}
		return res;
	}

	private org.sireum.aadl.ir.Component buildComponent(ComponentInstance compInst, List<String> path) {
		final List<String> currentPath = add(path, compInst.getName());

		final List<org.sireum.aadl.ir.Feature> features = compInst.getFeatureInstances().stream()
				.map(fi -> buildFeature(fi, currentPath)).collect(Collectors.toList());

		final List<org.sireum.aadl.ir.ConnectionInstance> connectionInstances = compInst.getConnectionInstances()
				.stream()
				.map(ci -> buildConnectionInst(ci, currentPath)).collect(Collectors.toList());

		final List<org.sireum.aadl.ir.Property> properties = compInst.getOwnedPropertyAssociations().stream()
				.map(pa -> buildProperty(pa, currentPath)).collect(Collectors.toList());

		final List<org.sireum.aadl.ir.Flow> flows = compInst.getFlowSpecifications().stream()
				.map(fs -> buildFlow(fs, currentPath)).collect(Collectors.toList());

		final List<org.sireum.aadl.ir.Component> subComponents = compInst.getComponentInstances().stream()
				.map(ci -> buildComponent(ci, currentPath)).collect(Collectors.toList());

		List<org.sireum.aadl.ir.Connection> connections = iList();
		if (compConnMap.containsKey(currentPath)) {
			connections = compConnMap.get(currentPath).stream()
					.flatMap(c -> buildConnection(c, currentPath, compInst).stream())
					.collect(Collectors.toList());
		}

		AadlASTJavaFactory.ComponentCategory category = null;
		switch(compInst.getCategory()) {
		case ABSTRACT: category = AadlASTJavaFactory.ComponentCategory.Abstract; break;
		case BUS: category = AadlASTJavaFactory.ComponentCategory.Bus; break;
		case DATA: category = AadlASTJavaFactory.ComponentCategory.Data; break;
		case DEVICE: category = AadlASTJavaFactory.ComponentCategory.Device; break;
		case MEMORY: category = AadlASTJavaFactory.ComponentCategory.Memory; break;
		case PROCESS: category = AadlASTJavaFactory.ComponentCategory.Process; break;
		case PROCESSOR: category = AadlASTJavaFactory.ComponentCategory.Processor; break;
		case SUBPROGRAM: category = AadlASTJavaFactory.ComponentCategory.Subprogram; break;
		case SUBPROGRAM_GROUP: category = AadlASTJavaFactory.ComponentCategory.SubprogramGroup; break;
		case SYSTEM: category = AadlASTJavaFactory.ComponentCategory.System; break;
		case THREAD: category = AadlASTJavaFactory.ComponentCategory.Thread; break;
		case THREAD_GROUP: category = AadlASTJavaFactory.ComponentCategory.ThreadGroup; break;
		case VIRTUAL_BUS: category = AadlASTJavaFactory.ComponentCategory.VirtualBus; break;
		case VIRTUAL_PROCESSOR: category = AadlASTJavaFactory.ComponentCategory.VirtualProcessor; break;
		default: throw new RuntimeException("Unexpected");
		}

		final org.sireum.aadl.ir.Name identifier = factory.name(currentPath);

		final org.sireum.aadl.ir.Classifier classifier = compInst.getClassifier() != null
				? factory.classifier(compInst.getClassifier().getQualifiedName())
				: null;

		final List<org.sireum.aadl.ir.Mode> modes = iList(); // TODO

		final List<org.sireum.aadl.ir.Annex> annexes = iList(); // TODO
		return factory.component(
				identifier, category, classifier, features, subComponents,
				connections, connectionInstances, properties, flows, modes, annexes);
	}

	private org.sireum.aadl.ir.Feature buildFeature(FeatureInstance featureInst, List<String> path) {

		final Feature f = featureInst.getFeature();

		final List<String> currentPath = add(path, featureInst.getName());

		org.sireum.aadl.ir.Classifier classifier = null;
		if(f.getFeatureClassifier() != null) {
			if(f.getFeatureClassifier() instanceof NamedElement) {
				classifier = factory.classifier(((NamedElement)f.getFeatureClassifier()).getQualifiedName());
			} else {
				throw new RuntimeException("Unexepcted classifier " + f.getFeatureClassifier() +
						" for feature " + featureInst.getQualifiedName());
			}
		}

		final List<org.sireum.aadl.ir.Property> properties = featureInst.getOwnedPropertyAssociations().stream()
				.map(pa -> buildProperty(pa, currentPath)).collect(Collectors.toList());

		AadlASTJavaFactory.FeatureCategory category = null;
		switch(featureInst.getCategory()) {
		case ABSTRACT_FEATURE: category = AadlASTJavaFactory.FeatureCategory.AbstractFeature; break;
		case BUS_ACCESS: category = AadlASTJavaFactory.FeatureCategory.BusAccess; break;
		case DATA_ACCESS: category = AadlASTJavaFactory.FeatureCategory.DataAccess; break;
		case DATA_PORT: category = AadlASTJavaFactory.FeatureCategory.DataPort; break;
		case EVENT_PORT: category = AadlASTJavaFactory.FeatureCategory.EventPort; break;
		case EVENT_DATA_PORT: category = AadlASTJavaFactory.FeatureCategory.EventDataPort; break;
		case FEATURE_GROUP: category = AadlASTJavaFactory.FeatureCategory.FeatureGroup; break;
		case PARAMETER:  category = AadlASTJavaFactory.FeatureCategory.Parameter; break;
		case SUBPROGRAM_ACCESS: category = AadlASTJavaFactory.FeatureCategory.SubprogramAccess; break;
		case SUBPROGRAM_GROUP_ACCESS: category = AadlASTJavaFactory.FeatureCategory.SubprogramAccessGroup; break;
		default: throw new RuntimeException("Unexpected category: " + featureInst.getCategory());
		}

		final org.sireum.aadl.ir.Name identifier = factory.name(currentPath);

		if((f instanceof DataPortImpl || f instanceof EventDataPortImpl) &&
				(f.getClassifier() instanceof DataTypeImpl || f.getClassifier() instanceof DataImplementationImpl)) {
			processDataType((DataClassifier) f.getClassifier());
		}

		final List<FeatureInstance> featureInstances = featureInst.getFeatureInstances();
		if(featureInstances.isEmpty()) {
			if(f instanceof AccessImpl) {
				final AccessImpl accessImpl = (AccessImpl) f;
				final AadlASTJavaFactory.AccessType accessType = accessImpl.getKind() == AccessType.PROVIDES
						? AadlASTJavaFactory.AccessType.Provides
						: AadlASTJavaFactory.AccessType.Requires;
				AadlASTJavaFactory.AccessCategory accessCategory = null;
				switch (accessImpl.getCategory()) {
				case BUS: accessCategory = AadlASTJavaFactory.AccessCategory.Bus; break;
				case DATA: accessCategory = AadlASTJavaFactory.AccessCategory.Data;	break;
				case SUBPROGRAM: accessCategory = AadlASTJavaFactory.AccessCategory.Subprogram; break;
				case SUBPROGRAM_GROUP: accessCategory = AadlASTJavaFactory.AccessCategory.SubprogramGroup; break;
				case VIRTUAL_BUS: accessCategory = AadlASTJavaFactory.AccessCategory.VirtualBus; break;
				}
				return factory.featureAccess(identifier, category, classifier, accessType, accessCategory, properties);
			} else if (f instanceof DirectedFeatureImpl) {
				final AadlASTJavaFactory.Direction direction = handleDirection(featureInst.getDirection());
				return factory.featureEnd(identifier, direction, category, classifier, properties);
			} else {
				throw new RuntimeException("Not expecting feature: " + featureInst);
			}
		} else {
			final boolean isInverse = ((FeatureGroupImpl) f).isInverse();
			final List<org.sireum.aadl.ir.Feature> features = featureInstances.stream()
					.map(fi -> buildFeature(fi, currentPath)).collect(Collectors.toList());
			return factory.featureGroup(identifier, features, isInverse, category, properties);
		}
	}

	private org.sireum.aadl.ir.ConnectionReference buildConnectionRef(ConnectionReference connRef, List<String> path) {
		final List<String> context = Arrays.asList(connRef.getContext().getInstanceObjectPath().split("\\."));
		final List<String> name = add(context, connRef.getConnection().getName());
		if (compConnMap.containsKey(context)) {
			compConnMap.put(context, add(compConnMap.get(context), connRef.getConnection()));
		} else {
			compConnMap.put(context, toISet(connRef.getConnection()));
		}
		return factory.connectionReference(factory.name(name), factory.name(context), path.equals(context));
	}

	private org.sireum.aadl.ir.ConnectionInstance buildConnectionInst(ConnectionInstance connInst, List<String> path) {
		final List<String> currentPath = add(path, connInst.getName());

		final List<String> srcComponent = Arrays
				.asList(connInst.getSource().getComponentInstance().getInstanceObjectPath().split("\\."));
		final List<String> srcFeature = Arrays.asList(connInst.getSource().getInstanceObjectPath().split("\\."));
		final AadlASTJavaFactory.Direction srcDirection = connInst.getSource() instanceof FeatureInstanceImpl
				? handleDirection(((FeatureInstanceImpl) connInst.getSource()).getDirection())
				: null;

		final List<String> dstComponent = Arrays
				.asList(connInst.getDestination().getComponentInstance().getInstanceObjectPath().split("\\."));
		final List<String> dstFeature = Arrays.asList(connInst.getDestination().getInstanceObjectPath().split("\\."));
		final AadlASTJavaFactory.Direction dstDirection =
				connInst.getDestination() instanceof FeatureInstanceImpl
						? handleDirection(((FeatureInstanceImpl) connInst.getDestination()).getDirection())
						: null;

		final org.sireum.aadl.ir.Name name = factory.name(currentPath);

		final List<org.sireum.aadl.ir.Property> properties = connInst.getOwnedPropertyAssociations().stream()
				.map(pa -> buildProperty(pa, currentPath)).collect(Collectors.toList());

		AadlASTJavaFactory.ConnectionKind kind = null;
		switch(connInst.getKind()) {
		case ACCESS_CONNECTION: kind = AadlASTJavaFactory.ConnectionKind.Access; break;
		case FEATURE_CONNECTION: kind = AadlASTJavaFactory.ConnectionKind.Feature; break;
		case FEATURE_GROUP_CONNECTION: kind = AadlASTJavaFactory.ConnectionKind.FeatureGroup; break;
		case MODE_TRANSITION_CONNECTION: kind = AadlASTJavaFactory.ConnectionKind.ModeTransition; break;
		case PARAMETER_CONNECTION: kind = AadlASTJavaFactory.ConnectionKind.Parameter; break;
		case PORT_CONNECTION: kind = AadlASTJavaFactory.ConnectionKind.Port; break;
		}

		final List<org.sireum.aadl.ir.ConnectionReference> connectionRefs = connInst.getConnectionReferences().stream()
				.map(ci -> buildConnectionRef(ci, path)).collect(Collectors.toList());

		final org.sireum.aadl.ir.EndPoint src = factory.endPoint(
				factory.name(srcComponent), factory.name(srcFeature), srcDirection);

		final org.sireum.aadl.ir.EndPoint dst = factory.endPoint(
				factory.name(dstComponent), factory.name(dstFeature), dstDirection);

    	return factory.connectionInstance(name, src, dst, kind, connectionRefs, properties);
	}

	private org.sireum.aadl.ir.Flow buildFlow(FlowSpecificationInstance flowInst, List<String> path) {

		final List<String> currentPath = add(path, flowInst.getQualifiedName());
		final org.sireum.aadl.ir.Name name = factory.name(currentPath);

		AadlASTJavaFactory.FlowKind kind = null;
		switch(flowInst.getFlowSpecification().getKind()) {
		case SOURCE: kind = AadlASTJavaFactory.FlowKind.Source; break;
		case SINK: kind = AadlASTJavaFactory.FlowKind.Sink; break;
		case PATH: kind = AadlASTJavaFactory.FlowKind.Path; break;
		}

		org.sireum.aadl.ir.Feature source = null;
		if(flowInst.getSource() != null) {
			//List<String> us = Arrays.asList(flowInst.getSource().getInstanceObjectPath().split("\\."));
			source = buildFeature(flowInst.getSource(), currentPath);
		}

		org.sireum.aadl.ir.Feature sink = null;
		if(flowInst.getDestination() != null) {
			//List<String> ud = Arrays.asList(flowInst.getDestination().getInstanceObjectPath().split("\\."));
			sink = buildFeature(flowInst.getDestination(), currentPath);
		}

		return factory.flow(name, kind, source, sink);
	}

	private org.sireum.aadl.ir.Property buildProperty(PropertyAssociation pa, List<String> path) {
		final Property prop = pa.getProperty();
		final List<String> currentPath = add(path, prop.getQualifiedName());
		final NamedElement cont = (NamedElement) pa.eContainer();

		List<org.sireum.aadl.ir.PropertyValue> propertyValues = iList();
		try {
			PropertyExpression pe = PropertyUtils.getSimplePropertyValue(cont, prop);
			propertyValues = getPropertyExpressionValue(pe, path);
		} catch (Throwable t) {
			java.lang.System.err.println("Error encountered while trying to fetch property value for " +
		      prop.getQualifiedName() + " from " + cont.getQualifiedName() + " : " + t.getMessage());
		}

		return factory.property(factory.name(currentPath), propertyValues);
	}

	private org.sireum.aadl.ir.UnitProp getUnitProp (NumberValue nv) {
		if(nv == null) {
			return factory.unitProp("??", null);
		} else {
			final double v = org.osate.aadl2.operations.NumberValueOperations.getScaledValue(nv);
			final UnitLiteral u = org.osate.aadl2.operations.UnitLiteralOperations.getAbsoluteUnit(nv.getUnit());
			return factory.unitProp(Double.toString(v), u == null ? null : u.getName());
		}
	}

	private List<org.sireum.aadl.ir.PropertyValue> getPropertyExpressionValue(PropertyExpression pe, List<String> path) {

		switch(pe.eClass().getClassifierID()) {
		case Aadl2Package.BOOLEAN_LITERAL:
			final String b = Boolean.toString(((BooleanLiteral) pe).getValue());
			return toIList(factory.valueProp(b));
		case Aadl2Package.INTEGER_LITERAL:
		case Aadl2Package.REAL_LITERAL:
			return toIList(getUnitProp((NumberValue) pe));
		 case Aadl2Package.STRING_LITERAL:
			final String v = ((StringLiteral) pe).getValue();
			 return toIList(factory.valueProp(v));
		 case Aadl2Package.RANGE_VALUE:
			final RangeValue rv = (RangeValue) pe;
			 return toIList(factory.rangeProp(
					 getUnitProp(rv.getMinimumValue()),
					 getUnitProp(rv.getMaximumValue())));
         case Aadl2Package.CLASSIFIER_VALUE:
			final Classifier cv = ((ClassifierValue) pe).getClassifier();
        	 return toIList(factory.classifierProp(cv.getQualifiedName()));
         case Aadl2Package.LIST_VALUE:
			final ListValue lv = (ListValue) pe;
			List<org.sireum.aadl.ir.PropertyValue> elems = iList();
        	 for(PropertyExpression e : lv.getOwnedListElements()) {
				elems = addAll(elems, getPropertyExpressionValue(e, path));
			}
        	 return elems;
         case Aadl2Package.NAMED_VALUE:
			final NamedValue nv = (NamedValue) pe;
			final AbstractNamedValue nv2 = nv.getNamedValue();

        	 switch(nv2.eClass().getClassifierID()) {
        	 case Aadl2Package.ENUMERATION_LITERAL:
				final EnumerationLiteral el = (EnumerationLiteral) nv2;
        		 return toIList(factory.valueProp(el.getFullName()));
        	 case Aadl2Package.PROPERTY:
				final Property _p = (Property) nv2;
        		 if(_p.getDefaultValue() != null) {
        			 return getPropertyExpressionValue(_p.getDefaultValue(), path);
        		 } else {
        			 return toIList(factory.valueProp(_p.getQualifiedName()));
        		 }
        	 case Aadl2Package.PROPERTY_CONSTANT:
				final PropertyConstant pc = (PropertyConstant) nv2;
        		 return toIList(factory.valueProp(pc.getConstantValue().toString()));
        	 default:
        		 java.lang.System.err.println("Not handling " + pe.eClass().getClassifierID() + " " + nv2);
        		 return iList();
        	 }
         case Aadl2Package.RECORD_VALUE:
			final RecordValue rvy = (RecordValue) pe;
			final List<org.sireum.aadl.ir.Property> properties = rvy.getOwnedFieldValues().stream()
					.map(fv -> factory.property(
        	    		 factory.name(add(path, fv.getProperty().getQualifiedName())),
        	    		 getPropertyExpressionValue(fv.getOwnedValue(), path))
        	 ).collect(Collectors.toList());
        	 return toIList(factory.recordProp(properties));
         case Aadl2Package.REFERENCE_VALUE:
			final ReferenceValue rvx = (ReferenceValue) pe;
			final org.sireum.aadl.ir.Name refName = factory.name(toIList(rvx.toString()));
        	 return toIList(factory.referenceProp(refName));
         case InstancePackage.INSTANCE_REFERENCE_VALUE:
             // FIXME: id is coming from InstancePackage rather than Aadl2Package.  Might cause the
             // following cast to fail if there is an id clash

			final InstanceReferenceValue irv = (InstanceReferenceValue) pe;
			final String t = irv.getReferencedInstanceObject().getInstanceObjectPath();

        	 return toIList(factory.referenceProp(factory.name(Arrays.asList(t.split("\\.")))));
         default:
        	 java.lang.System.err.println("Need to handle " + pe + " " + pe.eClass().getClassifierID());
        	 return toIList(factory.classifierProp(pe.getClass().getName()));
		}
	}

	private org.sireum.aadl.ir.Component processDataType(DataClassifier f) {
		final String name = f.getQualifiedName();
		if(datamap.containsKey(name)) {
			return datamap.get(name);
		}

		List<org.sireum.aadl.ir.Property> properties = f.getOwnedPropertyAssociations().stream().map(op ->
		buildProperty(op, iList())).collect(Collectors.toList());

		List<org.sireum.aadl.ir.Component> subComponents = iList();
		if(f instanceof DataTypeImpl) {
			// do nothing as component types can't have subcomponents
		} else if(f instanceof DataImplementation) {
			final DataImplementation di = (DataImplementation) f;
			final List<org.sireum.aadl.ir.Property> subProps = di.getType().getOwnedPropertyAssociations().stream()
					.map(op ->
					buildProperty(op, iList())).collect(Collectors.toList());
			properties = addAll(properties, subProps);

			for (Subcomponent subcom : di.getAllSubcomponents()) {
				if (!(subcom instanceof DataSubcomponent)) {
					throw new RuntimeException(
							"Unxepcted data subcomponent: " + subcom.getFullName() + " of type "
									+ subcom.getClass().getSimpleName() + " from " + f.getFullName());
				}

				DataSubcomponent dsc = (DataSubcomponent) subcom;

				final org.sireum.aadl.ir.Name subName = factory.name(toIList(dsc.getName()));
				final List<org.sireum.aadl.ir.Property> fProperties = dsc.getOwnedPropertyAssociations().stream()
						.map(op -> buildProperty(op, iList())).collect(Collectors.toList());

				final DataClassifier sct = (DataClassifier) dsc.getDataSubcomponentType();
				if (sct != null) {
					final org.sireum.aadl.ir.Component c = processDataType(sct);

					final List<org.sireum.aadl.ir.Property> cProps = addAll(isz2IList(c.properties()), fProperties);

					final AadlASTJavaFactory.ComponentCategory category = AadlASTJavaFactory.ComponentCategory
							.valueOf(c.category().name());

					final org.sireum.aadl.ir.Classifier classifier = c.classifier().nonEmpty() ? c.classifier().get()
							: null;

					final org.sireum.aadl.ir.Component sub = factory.component(subName,
							category, classifier, isz2IList(c.features()), isz2IList(c.subComponents()),
							isz2IList(c.connections()), isz2IList(c.connectionInstances()), cProps,
							isz2IList(c.flows()), isz2IList(c.modes()), isz2IList(c.annexes()));

					subComponents = add(subComponents, sub);
				} else {
					// type not specified for subcomponent/field
					final org.sireum.aadl.ir.Component sub = factory.component(subName, // name
							AadlASTJavaFactory.ComponentCategory.Data, // category
							null, // classifier
							iList(), // features
							iList(), // subComponents
							iList(), // connections
							iList(), // connectionInstances
							fProperties, // properties
							iList(), // flows
							iList(), // modes
							iList() // annexes
					);

					subComponents = add(subComponents, sub);
				}
			}
		} else {
			throw new RuntimeException("Unexpected data type: " + f);
		}

		final org.sireum.aadl.ir.Component c = factory.component(
				factory.name(iList()), //identifier
				AadlASTJavaFactory.ComponentCategory.Data, // category
				factory.classifier(name),
				iList(), // features
				subComponents,
				iList(), // connections
				iList(), // connectionInstances
				properties,
				iList(), //flows
				iList(), // modes
				iList() // annexes
				);
		datamap.put(name, c);
		return c;
	}

	private AadlASTJavaFactory.Direction handleDirection(DirectionType d) {
		AadlASTJavaFactory.Direction direction = null;
		switch(d) {
		case IN: direction = AadlASTJavaFactory.Direction.In; break;
		case OUT: direction = AadlASTJavaFactory.Direction.Out; break;
		case IN_OUT: direction = AadlASTJavaFactory.Direction.InOut; break;
		}
		return direction;
	}

	private <T> List<T> isz2IList(org.sireum.IS<org.sireum.Z, T> isz) {
		return Collections.unmodifiableList(scala.collection.JavaConverters.seqAsJavaList(isz.elements()));
	}

	private <T> List<T> toIList(T e) {
		final List<T> ret = new ArrayList<T>();
		ret.add(e);
		return Collections.unmodifiableList(ret);
	}

	private <T> List<T> add(List<T> l, T e) {
		final List<T> ret = new ArrayList<T>(l);
		ret.add(e);
		return Collections.unmodifiableList(ret);
	}

	private <T> List<T> addAll(List<T> l, List<T> e) {
		final List<T> ret = new ArrayList<T>(l);
		ret.addAll(e);
		return Collections.unmodifiableList(ret);
	}

	private <T> Set<T> toISet(T e) {
		final Set<T> ret = new LinkedHashSet<T>();
		ret.add(e);
		return Collections.unmodifiableSet(ret);
	}

	private <T> Set<T> add(Set<T> s, T e) {
		final Set<T> ret = new LinkedHashSet<T>(s);
		ret.add(e);
		return Collections.unmodifiableSet(ret);
	}

	private <T> List<T> iList() {
		return Collections.emptyList();
	}

	private <T> Set<T> iSet() {
		return Collections.emptySet();
	}
}