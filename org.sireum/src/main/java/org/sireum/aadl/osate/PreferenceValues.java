package org.sireum.aadl.osate;

import org.eclipse.jface.preference.IPreferenceStore;

public class PreferenceValues {

	public enum SerializerType {
		JSON, JSON_COMPACT, MSG_PACK
	}

	public enum Generators {
		SERIALIZE, GEN_ARSIT, GEN_AWAS, GEN_CAMKES
	}

	public static SerializerType getSERIALIZATION_METHOD_OPT() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		return SerializerType.valueOf(store.getString(PreferenceConstants.SERIALIZATION_METHOD_OPT));
	}


	public static boolean getARSIT_SERIALIZE_OPT() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		return store.getBoolean(PreferenceConstants.ARSIT_SERIALIZE_OPT);
	}

	public static String getARSIT_OUTPUT_FOLDER_OPT() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		return store.getString(PreferenceConstants.ARSIT_OUTPUT_FOLDER_OPT);
	}


	public static boolean getACT_SERIALIZE_OPT() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		return store.getBoolean(PreferenceConstants.ACT_SERIALIZE_OPT);
	}

	public static String getACT_OUTPUT_FOLDER_OPT() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		return store.getString(PreferenceConstants.ACT_OUTPUT_FOLDER_OPT);
	}
}

