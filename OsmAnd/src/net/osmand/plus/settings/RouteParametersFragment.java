package net.osmand.plus.settings;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;

import net.osmand.StateChangedListener;
import net.osmand.plus.ApplicationMode;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.OsmandSettings.BooleanPreference;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.SettingsBaseActivity;
import net.osmand.plus.activities.SettingsNavigationActivity;
import net.osmand.plus.routing.RouteProvider;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.settings.bottomsheets.RecalculateRouteInDeviationBottomSheet;
import net.osmand.plus.settings.preferences.ListPreferenceEx;
import net.osmand.plus.settings.preferences.MultiSelectBooleanPreference;
import net.osmand.plus.settings.preferences.SwitchPreferenceEx;
import net.osmand.router.GeneralRouter;
import net.osmand.router.GeneralRouter.RoutingParameter;
import net.osmand.router.GeneralRouter.RoutingParameterType;
import net.osmand.util.Algorithms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.osmand.plus.routepreparationmenu.RoutingOptionsHelper.DRIVING_STYLE;

public class RouteParametersFragment extends BaseSettingsFragment implements OnPreferenceChanged {

	public static final String TAG = RouteParametersFragment.class.getSimpleName();

	private static final String AVOID_ROUTING_PARAMETER_PREFIX = "avoid_";
	private static final String PREFER_ROUTING_PARAMETER_PREFIX = "prefer_";
	private static final String ROUTE_PARAMETERS_INFO = "route_parameters_info";
	private static final String ROUTE_PARAMETERS_IMAGE = "route_parameters_image";
	private static final String RELIEF_SMOOTHNESS_FACTOR = "relief_smoothness_factor";
	private static final String ROUTING_SHORT_WAY = "prouting_short_way";
	private static final String ROUTING_RECALC_DISTANCE= "routing_recalc_distance";

	public static final float DISABLE_MODE = -1.0f;
	public static final float DEFAULT_MODE = 0.0f;

	private List<RoutingParameter> avoidParameters = new ArrayList<RoutingParameter>();
	private List<RoutingParameter> preferParameters = new ArrayList<RoutingParameter>();
	private List<RoutingParameter> drivingStyleParameters = new ArrayList<RoutingParameter>();
	private List<RoutingParameter> reliefFactorParameters = new ArrayList<RoutingParameter>();
	private List<RoutingParameter> otherRoutingParameters = new ArrayList<RoutingParameter>();

	private StateChangedListener<Boolean> booleanRoutingPrefListener;
	private StateChangedListener<String> customRoutingPrefListener;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		booleanRoutingPrefListener = new StateChangedListener<Boolean>() {
			@Override
			public void stateChanged(Boolean change) {
				recalculateRoute();
			}
		};
		customRoutingPrefListener = new StateChangedListener<String>() {
			@Override
			public void stateChanged(String change) {
				recalculateRoute();
			}
		};
	}

	@Override
	protected void setupPreferences() {
		setupRouteParametersImage();

		Preference routeParametersInfo = findPreference(ROUTE_PARAMETERS_INFO);
		routeParametersInfo.setIcon(getContentIcon(R.drawable.ic_action_info_dark));
		routeParametersInfo.setTitle(getString(R.string.route_parameters_info, getSelectedAppMode().toHumanString()));

		setupRoutingPrefs();
	}

	@Override
	protected void onBindPreferenceViewHolder(Preference preference, PreferenceViewHolder holder) {
		super.onBindPreferenceViewHolder(preference, holder);

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
			return;
		}
		String key = preference.getKey();
		if (ROUTE_PARAMETERS_INFO.equals(key)) {
			int colorRes = isNightMode() ? R.color.activity_background_color_dark : R.color.activity_background_color_light;
			holder.itemView.setBackgroundColor(ContextCompat.getColor(app, colorRes));
		} else if (ROUTE_PARAMETERS_IMAGE.equals(key)) {
			ImageView imageView = (ImageView) holder.itemView.findViewById(R.id.device_image);
			if (imageView != null) {
				int bgResId = isNightMode() ? R.drawable.img_settings_device_bottom_dark : R.drawable.img_settings_device_bottom_light;
				Drawable layerDrawable = app.getUIUtilities().getLayeredIcon(bgResId, R.drawable.img_settings_sreen_route_parameters);

				imageView.setImageDrawable(layerDrawable);
			}
		}
	}

	private void setupRouteParametersImage() {
		Preference routeParametersImage = findPreference(ROUTE_PARAMETERS_IMAGE);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
			routeParametersImage.setVisible(false);
		}
	}

	private void setupTimeConditionalRoutingPref() {
		SwitchPreferenceEx timeConditionalRouting = createSwitchPreferenceEx(settings.ENABLE_TIME_CONDITIONAL_ROUTING.getId(),
				R.string.temporary_conditional_routing, R.layout.preference_with_descr_dialog_and_switch);
		timeConditionalRouting.setIcon(getRoutingPrefIcon(settings.ENABLE_TIME_CONDITIONAL_ROUTING.getId()));
		timeConditionalRouting.setSummaryOn(R.string.shared_string_on);
		timeConditionalRouting.setSummaryOff(R.string.shared_string_off);
		getPreferenceScreen().addPreference(timeConditionalRouting);
	}

	private void setupRoutingPrefs() {
		OsmandApplication app = getMyApplication();
		if (app == null) {
			return;
		}
		PreferenceScreen screen = getPreferenceScreen();

		ApplicationMode am = getSelectedAppMode();

		SwitchPreferenceEx fastRoute = createSwitchPreferenceEx(app.getSettings().FAST_ROUTE_MODE.getId(), R.string.fast_route_mode, R.layout.preference_with_descr_dialog_and_switch);
		fastRoute.setIcon(getRoutingPrefIcon(app.getSettings().FAST_ROUTE_MODE.getId()));
		fastRoute.setDescription(getString(R.string.fast_route_mode_descr));
		fastRoute.setSummaryOn(R.string.shared_string_on);
		fastRoute.setSummaryOff(R.string.shared_string_off);

		setupSelectRouteRecalcDistance(screen);

		if (am.getRouteService() == RouteProvider.RouteService.OSMAND){
			GeneralRouter router = app.getRouter(am);
			clearParameters();
			if (router != null) {
				Map<String, RoutingParameter> parameters = router.getParameters();
				if (!am.isDerivedRoutingFrom(ApplicationMode.CAR)) {
					screen.addPreference(fastRoute);
				}
				for (Map.Entry<String, RoutingParameter> e : parameters.entrySet()) {
					String param = e.getKey();
					RoutingParameter routingParameter = e.getValue();
					if (param.startsWith(AVOID_ROUTING_PARAMETER_PREFIX)) {
						avoidParameters.add(routingParameter);
					} else if (param.startsWith(PREFER_ROUTING_PARAMETER_PREFIX)) {
						preferParameters.add(routingParameter);
					} else if (RELIEF_SMOOTHNESS_FACTOR.equals(routingParameter.getGroup())) {
						reliefFactorParameters.add(routingParameter);
					} else if (DRIVING_STYLE.equals(routingParameter.getGroup())) {
						drivingStyleParameters.add(routingParameter);
					} else if ((!param.equals(GeneralRouter.USE_SHORTEST_WAY) || am.isDerivedRoutingFrom(ApplicationMode.CAR))
							&& !param.equals(GeneralRouter.VEHICLE_HEIGHT)
							&& !param.equals(GeneralRouter.VEHICLE_WEIGHT)
							&& !param.equals(GeneralRouter.VEHICLE_WIDTH)) {
						otherRoutingParameters.add(routingParameter);
					}
				}
				if (drivingStyleParameters.size() > 0) {
					ListPreferenceEx drivingStyleRouting = createRoutingBooleanListPreference(DRIVING_STYLE, drivingStyleParameters);
					screen.addPreference(drivingStyleRouting);
				}
				if (avoidParameters.size() > 0) {
					String title;
					String description;
					if (am.isDerivedRoutingFrom(ApplicationMode.PUBLIC_TRANSPORT)) {
						title = getString(R.string.avoid_pt_types);
						description = getString(R.string.avoid_pt_types_descr);
					} else {
						title = getString(R.string.impassable_road);
						description = getString(R.string.avoid_in_routing_descr_);
					}
					MultiSelectBooleanPreference avoidRouting = createRoutingBooleanMultiSelectPref(AVOID_ROUTING_PARAMETER_PREFIX, title, description, avoidParameters);
					screen.addPreference(avoidRouting);
				}
				if (preferParameters.size() > 0) {
					String title = getString(R.string.prefer_in_routing_title);
					String descr = getString(R.string.prefer_in_routing_descr);
					MultiSelectBooleanPreference preferRouting = createRoutingBooleanMultiSelectPref(PREFER_ROUTING_PARAMETER_PREFIX, title, descr, preferParameters);
					screen.addPreference(preferRouting);
				}
				if (reliefFactorParameters.size() > 0) {
					ListPreferenceEx reliefFactorRouting = createRoutingBooleanListPreference(RELIEF_SMOOTHNESS_FACTOR, reliefFactorParameters);
					reliefFactorRouting.setDescription(R.string.relief_smoothness_factor_descr);

					screen.addPreference(reliefFactorRouting);
				}
				for (RoutingParameter p : otherRoutingParameters) {
					String title = SettingsBaseActivity.getRoutingStringPropertyName(app, p.getId(), p.getName());
					String description = SettingsBaseActivity.getRoutingStringPropertyDescription(app, p.getId(), p.getDescription());

					if (p.getType() == RoutingParameterType.BOOLEAN) {
						OsmandSettings.OsmandPreference pref = settings.getCustomRoutingBooleanProperty(p.getId(), p.getDefaultBoolean());

						SwitchPreferenceEx switchPreferenceEx = (SwitchPreferenceEx) createSwitchPreferenceEx(pref.getId(), title, description, R.layout.preference_with_descr_dialog_and_switch);
						switchPreferenceEx.setDescription(description);
						switchPreferenceEx.setIcon(getRoutingPrefIcon(p.getId()));
						switchPreferenceEx.setSummaryOn(R.string.shared_string_on);
						switchPreferenceEx.setSummaryOff(R.string.shared_string_off);

						screen.addPreference(switchPreferenceEx);
					} else {
						Object[] vls = p.getPossibleValues();
						String[] svlss = new String[vls.length];
						int i = 0;
						for (Object o : vls) {
							svlss[i++] = o.toString();
						}
						OsmandSettings.OsmandPreference pref = settings.getCustomRoutingProperty(p.getId(), p.getType() == RoutingParameterType.NUMERIC ? "0.0" : "-");

						ListPreferenceEx listPreferenceEx = (ListPreferenceEx) createListPreferenceEx(pref.getId(), p.getPossibleValueDescriptions(), svlss, title, R.layout.preference_with_descr);
						listPreferenceEx.setDescription(description);
						listPreferenceEx.setIcon(getRoutingPrefIcon(p.getId()));

						screen.addPreference(listPreferenceEx);
					}
				}
			}
			setupTimeConditionalRoutingPref();
		} else if (am.getRouteService() == RouteProvider.RouteService.BROUTER) {
			screen.addPreference(fastRoute);
			setupTimeConditionalRoutingPref();
		} else if (am.getRouteService() == RouteProvider.RouteService.STRAIGHT) {
			Preference straightAngle = new Preference(app.getApplicationContext());
			straightAngle.setPersistent(false);
			straightAngle.setKey(settings.ROUTE_STRAIGHT_ANGLE.getId());
			straightAngle.setTitle(getString(R.string.recalc_angle_dialog_title));
			straightAngle.setSummary(String.format(getString(R.string.shared_string_angle_param), (int) am.getStrAngle()));
			straightAngle.setLayoutResource(R.layout.preference_with_descr);
			straightAngle.setIcon(getRoutingPrefIcon("routing_recalc_distance")); //TODO change for appropriate icon when available
			getPreferenceScreen().addPreference(straightAngle);
		}
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference.getKey().equals(settings.ROUTE_STRAIGHT_ANGLE.getId())) {
			showSeekbarSettingsDialog(getActivity(), getSelectedAppMode());
		}
		return super.onPreferenceClick(preference);
	}

	@Override
	public void onDisplayPreferenceDialog(Preference preference) {
		if (preference.getKey().equals(settings.ROUTE_RECALCULATION_DISTANCE.getId())) {
			FragmentManager fragmentManager = getFragmentManager();
			if (fragmentManager != null) {
				RecalculateRouteInDeviationBottomSheet.showInstance(getFragmentManager(), preference.getKey(), this, false, getSelectedAppMode());
			}
		} else {
			super.onDisplayPreferenceDialog(preference);
		}
	}

	private void showSeekbarSettingsDialog(Activity activity, final ApplicationMode mode) {
		if (activity == null || mode == null) {
			return;
		}
		final OsmandApplication app = (OsmandApplication) activity.getApplication();
		final float[] angleValue = new float[] {mode.getStrAngle()};
		boolean nightMode = !app.getSettings().isLightContentForMode(mode);
		Context themedContext = UiUtilities.getThemedContext(activity, nightMode);
		AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
		View seekbarView = LayoutInflater.from(themedContext).inflate(R.layout.recalculation_angle_dialog, null, false);
		builder.setView(seekbarView);
		builder.setPositiveButton(R.string.shared_string_ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				mode.setStrAngle(angleValue[0]);
				updateAllSettings();
				RoutingHelper routingHelper = app.getRoutingHelper();
				if (mode.equals(routingHelper.getAppMode()) && (routingHelper.isRouteCalculated() || routingHelper.isRouteBeingCalculated())) {
					routingHelper.recalculateRouteDueToSettingsChange();
				}
			}
		});
		builder.setNegativeButton(R.string.shared_string_cancel, null);

		int selectedModeColor = ContextCompat.getColor(app, mode.getIconColorInfo().getColor(nightMode));
		setupAngleSlider(angleValue, seekbarView, nightMode, selectedModeColor);
		builder.show();
	}

	private static void setupAngleSlider(final float[] angleValue,
	                                     View seekbarView,
	                                     final boolean nightMode,
	                                     final int activeColor) {

		final SeekBar angleBar = seekbarView.findViewById(R.id.angle_seekbar);
		final TextView angleTv = seekbarView.findViewById(R.id.angle_text);

		angleTv.setText(String.valueOf(angleValue[0]));
		angleBar.setProgress((int) angleValue[0]);
		angleBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				int value = progress - (progress % 5);
				angleValue[0] = value;
				angleTv.setText(String.valueOf(value));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
		});
		UiUtilities.setupSeekBar(angleBar, activeColor, nightMode);
	}

	private void setupSelectRouteRecalcDistance(PreferenceScreen screen) {
		final SwitchPreferenceEx switchPref = createSwitchPreferenceEx(ROUTING_RECALC_DISTANCE,
				R.string.route_recalculation_dist_title, R.layout.preference_with_descr_dialog_and_switch);
		switchPref.setIcon(getRoutingPrefIcon(ROUTING_RECALC_DISTANCE));
		screen.addPreference(switchPref);
		updateRouteRecalcDistancePref();
	}

	private void updateRouteRecalcDistancePref() {
		SwitchPreferenceEx switchPref = (SwitchPreferenceEx) findPreference(ROUTING_RECALC_DISTANCE);
		if (switchPref == null) {
			return;
		}
		ApplicationMode appMode = getSelectedAppMode();
		float allowedValue = settings.ROUTE_RECALCULATION_DISTANCE.getModeValue(appMode);
		boolean enabled = allowedValue != DISABLE_MODE;
		if (allowedValue <= 0) {
			allowedValue = RoutingHelper.getDefaultAllowedDeviation(settings, appMode, RoutingHelper.getPosTolerance(0));
		}
		String summary = String.format(getString(R.string.ltr_or_rtl_combine_via_bold_point),
				enabled ? getString(R.string.shared_string_enabled) : getString(R.string.shared_string_disabled),
				OsmAndFormatter.getFormattedDistance(allowedValue, app, false));
		switchPref.setSummary(summary);
		switchPref.setChecked(enabled);
	}

	@Override
	public void onResume() {
		super.onResume();
		addRoutingPrefListeners();
	}

	@Override
	public void onPause() {
		super.onPause();
		removeRoutingPrefListeners();
	}

	@Override
	public void onAppModeChanged(ApplicationMode appMode) {
		removeRoutingPrefListeners();
		super.onAppModeChanged(appMode);
		addRoutingPrefListeners();
	}

	private void addRoutingPrefListeners() {
		settings.FAST_ROUTE_MODE.addListener(booleanRoutingPrefListener);
		settings.ENABLE_TIME_CONDITIONAL_ROUTING.addListener(booleanRoutingPrefListener);

		for (RoutingParameter parameter : otherRoutingParameters) {
			if (parameter.getType() == RoutingParameterType.BOOLEAN) {
				OsmandSettings.CommonPreference<Boolean> pref = settings.getCustomRoutingBooleanProperty(parameter.getId(), parameter.getDefaultBoolean());
				pref.addListener(booleanRoutingPrefListener);
			} else {
				OsmandSettings.CommonPreference<String> pref = settings.getCustomRoutingProperty(parameter.getId(), parameter.getType() == RoutingParameterType.NUMERIC ? "0.0" : "-");
				pref.addListener(customRoutingPrefListener);
			}
		}
	}

	private void removeRoutingPrefListeners() {
		settings.FAST_ROUTE_MODE.removeListener(booleanRoutingPrefListener);
		settings.ENABLE_TIME_CONDITIONAL_ROUTING.removeListener(booleanRoutingPrefListener);

		for (RoutingParameter parameter : otherRoutingParameters) {
			if (parameter.getType() == RoutingParameterType.BOOLEAN) {
				OsmandSettings.CommonPreference<Boolean> pref = settings.getCustomRoutingBooleanProperty(parameter.getId(), parameter.getDefaultBoolean());
				pref.removeListener(booleanRoutingPrefListener);
			} else {
				OsmandSettings.CommonPreference<String> pref = settings.getCustomRoutingProperty(parameter.getId(), parameter.getType() == RoutingParameterType.NUMERIC ? "0.0" : "-");
				pref.removeListener(customRoutingPrefListener);
			}
		}
	}

	@Override
	public void onApplyPreferenceChange(String prefId, boolean applyToAllProfiles, Object newValue) {
		if ((RELIEF_SMOOTHNESS_FACTOR.equals(prefId) || DRIVING_STYLE.equals(prefId)) && newValue instanceof String) {
			ApplicationMode appMode = getSelectedAppMode();
			String selectedParameterId = (String) newValue;
			List<RoutingParameter> routingParameters = DRIVING_STYLE.equals(prefId) ? drivingStyleParameters : reliefFactorParameters;
			for (RoutingParameter p : routingParameters) {
				String parameterId = p.getId();
				SettingsNavigationActivity.setRoutingParameterSelected(settings, appMode, parameterId, p.getDefaultBoolean(), parameterId.equals(selectedParameterId));
			}
			recalculateRoute();
		} else if (ROUTING_SHORT_WAY.equals(prefId) && newValue instanceof Boolean) {
			applyPreference(settings.FAST_ROUTE_MODE.getId(), applyToAllProfiles, !(Boolean) newValue);
		} else if (ROUTING_RECALC_DISTANCE.equals(prefId)) {
			boolean enabled = false;
			float valueToSave = DISABLE_MODE;
			if (newValue instanceof Boolean) {
				enabled = (boolean) newValue;
				valueToSave = enabled ? DEFAULT_MODE : DISABLE_MODE;
			} else if (newValue instanceof Float) {
				valueToSave = (float) newValue;
				enabled = valueToSave != DISABLE_MODE;
			}
			applyPreference(ROUTING_RECALC_DISTANCE, applyToAllProfiles, valueToSave);
			applyPreference(settings.DISABLE_OFFROUTE_RECALC.getId(), applyToAllProfiles, !enabled);
			updateRouteRecalcDistancePref();
		} else {
			super.onApplyPreferenceChange(prefId, applyToAllProfiles, newValue);
		}
	}

	@Override
	public void onPreferenceChanged(String prefId) {
		if (AVOID_ROUTING_PARAMETER_PREFIX.equals(prefId) || PREFER_ROUTING_PARAMETER_PREFIX.equals(prefId)) {
			recalculateRoute();
		}
	}

	private ListPreferenceEx createRoutingBooleanListPreference(String groupKey, List<RoutingParameter> routingParameters) {
		String defaultTitle = Algorithms.capitalizeFirstLetterAndLowercase(groupKey.replace('_', ' '));
		String title = SettingsBaseActivity.getRoutingStringPropertyName(app, groupKey, defaultTitle);
		ApplicationMode am = getSelectedAppMode();

		Object[] entryValues = new Object[routingParameters.size()];
		String[] entries = new String[entryValues.length];

		String selectedParameterId = null;
		for (int i = 0; i < routingParameters.size(); i++) {
			RoutingParameter parameter = routingParameters.get(i);
			entryValues[i] = parameter.getId();
			entries[i] = SettingsNavigationActivity.getRoutinParameterTitle(app, parameter);
			if (SettingsNavigationActivity.isRoutingParameterSelected(settings, am, parameter)) {
				selectedParameterId = parameter.getId();
			}
		}

		ListPreferenceEx routingListPref = createListPreferenceEx(groupKey, entries, entryValues, title, R.layout.preference_with_descr);
		routingListPref.setPersistent(false);
		routingListPref.setValue(selectedParameterId);
		routingListPref.setIcon(getRoutingPrefIcon(groupKey));

		return routingListPref;
	}

	private MultiSelectBooleanPreference createRoutingBooleanMultiSelectPref(String groupKey, String title, String descr, List<RoutingParameter> routingParameters) {
		MultiSelectBooleanPreference multiSelectPref = new MultiSelectBooleanPreference(app);
		multiSelectPref.setKey(groupKey);
		multiSelectPref.setTitle(title);
		multiSelectPref.setSummary(descr);
		multiSelectPref.setDescription(descr);
		multiSelectPref.setLayoutResource(R.layout.preference_with_descr);
		multiSelectPref.setIcon(getRoutingPrefIcon(groupKey));
		multiSelectPref.setIconSpaceReserved(true);

		String[] entries = new String[routingParameters.size()];
		String[] prefsIds = new String[routingParameters.size()];
		Set<String> enabledPrefsIds = new HashSet<>();

		ApplicationMode selectedMode = getSelectedAppMode();
		for (int i = 0; i < routingParameters.size(); i++) {
			RoutingParameter p = routingParameters.get(i);
			BooleanPreference booleanRoutingPref = (BooleanPreference) settings.getCustomRoutingBooleanProperty(p.getId(), p.getDefaultBoolean());

			entries[i] = SettingsBaseActivity.getRoutingStringPropertyName(app, p.getId(), p.getName());
			prefsIds[i] = booleanRoutingPref.getId();

			if (booleanRoutingPref.getModeValue(selectedMode)) {
				enabledPrefsIds.add(booleanRoutingPref.getId());
			}
		}

		multiSelectPref.setEntries(entries);
		multiSelectPref.setEntryValues(prefsIds);
		multiSelectPref.setValues(enabledPrefsIds);

		return multiSelectPref;
	}

	private void recalculateRoute() {
		RoutingHelper routingHelper = app.getRoutingHelper();
		if (getSelectedAppMode().equals(routingHelper.getAppMode())
				&& (routingHelper.isRouteCalculated() || routingHelper.isRouteBeingCalculated())) {
			routingHelper.recalculateRouteDueToSettingsChange();
		}
	}

	private void clearParameters() {
		avoidParameters.clear();
		preferParameters.clear();
		drivingStyleParameters.clear();
		reliefFactorParameters.clear();
		otherRoutingParameters.clear();
	}

	private Drawable getRoutingPrefIcon(String prefId) {
		switch (prefId) {
			case GeneralRouter.ALLOW_PRIVATE:
				return getPersistentPrefIcon(R.drawable.ic_action_private_access);
			case GeneralRouter.USE_SHORTEST_WAY:
				return getPersistentPrefIcon(R.drawable.ic_action_fuel);
			case GeneralRouter.ALLOW_MOTORWAYS:
				Drawable disabled = getContentIcon(R.drawable.ic_action_avoid_motorways);
				Drawable enabled = getActiveIcon(R.drawable.ic_action_motorways);
				return getPersistentPrefIcon(enabled, disabled);
			case GeneralRouter.USE_HEIGHT_OBSTACLES:
			case RELIEF_SMOOTHNESS_FACTOR:
				return getPersistentPrefIcon(R.drawable.ic_action_elevation);
			case AVOID_ROUTING_PARAMETER_PREFIX:
				return getPersistentPrefIcon(R.drawable.ic_action_alert);
			case DRIVING_STYLE:
				return getPersistentPrefIcon(R.drawable.ic_action_bicycle_dark);
			case "fast_route_mode":
				return getPersistentPrefIcon(R.drawable.ic_action_fastest_route);
			case "enable_time_conditional_routing":
				return getPersistentPrefIcon(R.drawable.ic_action_road_works_dark);
			case ROUTING_RECALC_DISTANCE:
				return getPersistentPrefIcon(R.drawable.ic_action_minimal_distance);

			default:
				return null;
		}
	}
}