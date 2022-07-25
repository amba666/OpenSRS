package com.vijay.jsonwizard.widgets;

import static com.vijay.jsonwizard.constants.JsonFormConstants.CALCULATION;
import static com.vijay.jsonwizard.constants.JsonFormConstants.CONSTRAINTS;
import static com.vijay.jsonwizard.constants.JsonFormConstants.FIELDS;
import static com.vijay.jsonwizard.constants.JsonFormConstants.FIELDS_TO_USE_VALUE;
import static com.vijay.jsonwizard.constants.JsonFormConstants.KEY;
import static com.vijay.jsonwizard.constants.JsonFormConstants.LABEL;
import static com.vijay.jsonwizard.constants.JsonFormConstants.READ_ONLY;
import static com.vijay.jsonwizard.constants.JsonFormConstants.RELEVANCE;
import static com.vijay.jsonwizard.constants.JsonFormConstants.STEP1;
import static com.vijay.jsonwizard.constants.JsonFormConstants.TYPE;
import static com.vijay.jsonwizard.constants.JsonFormConstants.VALUE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.rey.material.util.ViewUtil;
import com.rey.material.widget.Button;
import com.vijay.jsonwizard.R;
import com.vijay.jsonwizard.constants.JsonFormConstants.OPTIBPCONSTANTS;
import com.vijay.jsonwizard.domain.WidgetArgs;
import com.vijay.jsonwizard.fragments.JsonFormFragment;
import com.vijay.jsonwizard.interfaces.CommonListener;
import com.vijay.jsonwizard.interfaces.FormWidgetFactory;
import com.vijay.jsonwizard.interfaces.JsonApi;
import com.vijay.jsonwizard.interfaces.OnActivityResultListener;
import com.vijay.jsonwizard.utils.FormUtils;
import com.vijay.jsonwizard.utils.Utils;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import timber.log.Timber;

public class OptiBPWidgetFactory implements FormWidgetFactory {

    @Override
    public List<View> getViewsFromJson(String stepName, Context context, JsonFormFragment formFragment, JSONObject jsonObject, CommonListener listener) throws Exception {
        return getViewsFromJson(stepName, context, formFragment, jsonObject, listener, false);
    }

    @Override
    public List<View> getViewsFromJson(String stepName, Context context, JsonFormFragment formFragment, JSONObject jsonObject, CommonListener listener, boolean popup) throws Exception {
        WidgetArgs widgetArgs = new WidgetArgs();
        widgetArgs.withStepName(stepName).withContext(context).withFormFragment(formFragment)
                .withJsonObject(jsonObject).withListener(listener).withPopup(popup);

        JSONArray canvasIds = new JSONArray();

        boolean readOnly = false;
        if (jsonObject.has(READ_ONLY)) {
            readOnly = jsonObject.getBoolean(READ_ONLY);
        }

        LinearLayout rootLayout = getRootLayout(context);
        setWidgetTags(rootLayout, canvasIds, widgetArgs);
        attachRefreshLogic(context, jsonObject, rootLayout);

        initBPFieldsKeys(jsonObject);
        EditText systolicBPEditText = getBPEditTextField(widgetArgs, BPFieldType.SYSTOLIC_BP);
        EditText diastolicBPEditText = getBPEditTextField(widgetArgs, BPFieldType.DIASTOLIC_BP);

        TextView labelTextView = initLabel(rootLayout, widgetArgs, readOnly);
        setWidgetTags(labelTextView, canvasIds, widgetArgs);

        boolean isRepeat = isRepeatMeasurement(BPFieldType.SYSTOLIC_BP, BPFieldType.DIASTOLIC_BP);
        Button launchButton = initLaunchButton(rootLayout, widgetArgs, readOnly, getRequestCode(isRepeat));
        setWidgetTags(launchButton, canvasIds, widgetArgs);

        setGlobalLayoutListener(rootLayout, systolicBPEditText, diastolicBPEditText);
        setUpOptiBpActivityResultListener(widgetArgs, getRequestCode(isRepeat), rootLayout, systolicBPEditText, diastolicBPEditText);

        ((JsonApi) context).addFormDataView(rootLayout);

        rootLayout.setTag(R.id.canvas_ids, canvasIds.toString());

        List<View> views = new ArrayList<>(1);
        views.add(rootLayout);
        return views;
    }

    private void setWidgetTags(View view, JSONArray canvasIds, WidgetArgs widgetArgs) throws JSONException {
        JSONObject jsonObject = widgetArgs.getJsonObject();
        FormUtils.setViewOpenMRSEntityAttributes(jsonObject, view);

        view.setId(ViewUtil.generateViewId());
        view.setTag(R.id.key, jsonObject.getString(KEY));
        view.setTag(R.id.type, widgetArgs.getJsonObject().getString(TYPE));
        view.setTag(R.id.extraPopup, widgetArgs.isPopup());
        view.setTag(R.id.address, widgetArgs.getStepName() + ":" + jsonObject.getString(KEY));
        canvasIds.put(view.getId());
    }

    private void initBPFieldsKeys(JSONObject jsonObject) throws JSONException {
        if (jsonObject.has(FIELDS_TO_USE_VALUE)
                && jsonObject.getJSONArray(FIELDS_TO_USE_VALUE).length() == 2) {
            JSONArray fields = jsonObject.getJSONArray(FIELDS_TO_USE_VALUE);
            BPFieldType.SYSTOLIC_BP.setKey(fields.get(0).toString());
            BPFieldType.DIASTOLIC_BP.setKey(fields.get(1).toString());
        } else {
            Timber.e("No field values defined to populate BP values");
        }
    }

    private TextView initLabel(LinearLayout rootLayout, WidgetArgs widgetArgs, boolean readOnly) throws JSONException {
        Context context = widgetArgs.getContext();
        TextView labelTextView = rootLayout.findViewById(R.id.optibp_label);
        labelTextView.setText(getLabelText(context, widgetArgs.getJsonObject()));
        if (readOnly) {
            labelTextView.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray));
        }
        return labelTextView;
    }

    private Button initLaunchButton(LinearLayout rootLayout, final WidgetArgs widgetArgs, boolean readOnly, final int requestCode) throws JSONException {
        final Context context = widgetArgs.getContext();
        final JSONObject jsonObject = widgetArgs.getJsonObject();
        Button launchButton = rootLayout.findViewById(R.id.optibp_launch_button);
        formatButtonWidget(launchButton, widgetArgs.getJsonObject());
        launchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Timber.w(" ONCLICK WITH JSON %s", jsonObject);
                    Intent intent = new Intent(OPTIBPCONSTANTS.OPTIBP_LAUNCH_INTENT);
                    intent.setType("text/json");
                    intent.putExtra(Intent.EXTRA_TEXT, getInputJsonString(context, jsonObject, widgetArgs));
                    ((Activity) context).startActivityForResult(Intent.createChooser(intent, ""), requestCode);
                } catch (Exception e) {
                    Timber.e(e);
                }
            }
        });

        if (readOnly) {
            launchButton.setBackgroundDrawable(new ColorDrawable(widgetArgs.getContext().getResources()
                    .getColor(android.R.color.darker_gray)));
            launchButton.setClickable(false);
            launchButton.setEnabled(false);
            launchButton.setFocusable(false);
        }

        return launchButton;
    }

    private void formatButtonWidget(Button button, JSONObject jsonObject) throws JSONException {
        if (jsonObject.has(OPTIBPCONSTANTS.OPTIBP_KEY_BUTTON_BG_COLOR)) {
            String colorString = jsonObject.getString(OPTIBPCONSTANTS.OPTIBP_KEY_BUTTON_BG_COLOR);
            setButtonBgColor(button, colorString);
        }
        if (jsonObject.has(OPTIBPCONSTANTS.OPTIBP_KEY_BUTTON_TEXT_COLOR)) {
            String colorString = jsonObject.getString(OPTIBPCONSTANTS.OPTIBP_KEY_BUTTON_TEXT_COLOR);
            button.setTextColor(Color.parseColor(colorString));
        }
        if (jsonObject.has(OPTIBPCONSTANTS.OPTIBP_KEY_BUTTON_TEXT)) {
            String buttonText = jsonObject.getString(OPTIBPCONSTANTS.OPTIBP_KEY_BUTTON_TEXT);
            button.setText(buttonText);
        }

    }

    public void setUpOptiBpActivityResultListener(final WidgetArgs widgetArgs, int requestCode, final LinearLayout rootLayout, final EditText systolicEditText, final EditText diastolicEditText) {
        final Context context = widgetArgs.getContext();
        if (context instanceof JsonApi) {
            final JsonApi jsonApi = (JsonApi) context;
            jsonApi.addOnActivityResultListener(requestCode, new OnActivityResultListener() {
                @Override
                public void onActivityResult(int requestCode, int resultCode, Intent data) {
                    if (resultCode == Activity.RESULT_OK) {
                        if (requestCode == OPTIBPCONSTANTS.OPTIBP_REQUEST_CODE ||
                                requestCode == OPTIBPCONSTANTS.OPTIBP_REPEAT_REQUEST_CODE) {
                            try {
                                if (data != null) {
                                    try {
                                        String resultJson = data.getStringExtra(Intent.EXTRA_TEXT);
                                        Timber.d("Resultant OptiBP JSON: %s ", resultJson);
                                            populateBPEditTextValues(resultJson, systolicEditText, diastolicEditText, widgetArgs);
                                        writeResult(jsonApi, rootLayout, getComparatives(resultJson), widgetArgs);
                                    } catch (JSONException e) {
                                        Timber.e(e);
                                    }
                                } else
                                    Timber.e("NO RESULT FROM OPTIBP APP");
                            } catch (Exception e) {
                                Timber.e(e);
                            }
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.optibp_unable_to_receive), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void setGlobalLayoutListener(final LinearLayout rootLayout, final EditText systolicBPEditText, final EditText diastolicBPEditText) {
        if (systolicBPEditText != null && diastolicBPEditText != null) {
            rootLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (rootLayout.getVisibility() != View.VISIBLE
                            && TextUtils.isEmpty(systolicBPEditText.getText())
                            && TextUtils.isEmpty(diastolicBPEditText.getText())) {
                        Timber.i("OptiBP widget not visible");
                        toggleEditTextEnabled(systolicBPEditText, true);
                        toggleEditTextEnabled(diastolicBPEditText, true);
                    }
                }
            });
        }
    }

    private void setButtonBgColor(Button button, String colorString) {
        Drawable background = button.getBackground();
        if (background instanceof ShapeDrawable) {
            ((ShapeDrawable) background).getPaint().setColor(Color.parseColor(colorString));
        } else if (background instanceof GradientDrawable) {
            ((GradientDrawable) background).setColor(Color.parseColor(colorString));
        } else if (background instanceof ColorDrawable) {
            ((ColorDrawable) background).setColor(Color.parseColor(colorString));
        }
    }

    private void writeResult(JsonApi jsonApi, LinearLayout rootLayout, String resultJson, WidgetArgs widgetArgs) throws JSONException {
        // Write result JSON to widget value
        String key = (String) rootLayout.getTag(R.id.key);
        String openMrsEntityParent = (String) rootLayout.getTag(R.id.openmrs_entity_parent);
        String openMrsEntity = (String) rootLayout.getTag(R.id.openmrs_entity);
        String openMrsEntityId = (String) rootLayout.getTag(R.id.openmrs_entity_id);
        jsonApi.writeValue(widgetArgs.getStepName(), key, resultJson, openMrsEntityParent,
                openMrsEntity, openMrsEntityId, widgetArgs.isPopup());
    }

    protected void populateBPEditTextValues(String resultJsonString, EditText systolicBPEditText, EditText diastolicBPEditText, WidgetArgs widgetArgs) throws JSONException {
        if (systolicBPEditText != null) {
            systolicBPEditText.setText(getBPValue(resultJsonString, BPFieldType.SYSTOLIC_BP));
            toggleEditTextEnabled(systolicBPEditText, false);
        }
        if (diastolicBPEditText != null) {
            diastolicBPEditText.setText(getBPValue(resultJsonString, BPFieldType.DIASTOLIC_BP));
            toggleEditTextEnabled(diastolicBPEditText, false);
        }
        JSONObject calibrationObject = FormUtils.getFieldFromForm(widgetArgs.getFormFragment().getJsonApi().getmJSONObject(), OPTIBPCONSTANTS.OPTIBP_KEY_CALIBRATION_DATA);
        if (calibrationObject != null) {
            if (getComparatives(resultJsonString) != null) {
                calibrationObject.put(VALUE, getComparatives(resultJsonString));
            }
        }
    }

    private void toggleEditTextEnabled(EditText editText, boolean enabled) {
        editText.setEnabled(enabled);
        editText.setClickable(enabled);
        editText.setFocusable(enabled);
        editText.setFocusableInTouchMode(enabled);
    }

    protected String getBPValue(String resultJsonString, BPFieldType field) throws JSONException {
        JSONObject jsonObject = new JSONObject(resultJsonString);
        JSONArray result = jsonObject.getJSONArray(OPTIBPCONSTANTS.OPTIBP_REPORT_RESULT);
        JSONObject resultObject = result.getJSONObject(0);
        JSONArray component = resultObject.getJSONArray(OPTIBPCONSTANTS.OPTIBP_REPORT_COMPONENT);
        JSONObject bpComponent = ((JSONObject) component.get(BPFieldType.SYSTOLIC_BP.equals(field) ? 1 : 0));
        JSONObject valueQuantity = bpComponent.getJSONObject(OPTIBPCONSTANTS.OPTIBP_REPORT_VALUE_QUANTITY);
        int value = valueQuantity.getInt(VALUE);
        return String.valueOf(value);
    }

    protected String getComparatives(String resultJsonString) throws JSONException {
        JSONObject jsonObject = new JSONObject(resultJsonString);
        JSONArray result = jsonObject.getJSONArray(OPTIBPCONSTANTS.OPTIBP_REPORT_RESULT);
        JSONObject resultObject = result.getJSONObject(0);
        JSONArray component = resultObject.getJSONArray(OPTIBPCONSTANTS.OPTIBP_REPORT_COMPONENT);
        JSONObject secondIndex = component.optJSONObject(2);
        String valueString = secondIndex.optString(OPTIBPCONSTANTS.OPTIBP_VALUE_STRING);
        JSONObject valueObject = new JSONObject(valueString);
        JSONArray returnArray = valueObject.optJSONArray(OPTIBPCONSTANTS.COMPERATIVES);
        return returnArray != null ? returnArray.toString() : "";

    }

    protected EditText getBPEditTextField(WidgetArgs widgetArgs, BPFieldType field) {
        Context context = widgetArgs.getContext();
        EditText bpEditText = (EditText) widgetArgs.getFormFragment().getJsonApi().getFormDataView(widgetArgs.getStepName() + ":" + field.getKey());
        if (bpEditText == null) {
            Toast.makeText(context, context.getString(R.string.optibp_values_error), Toast.LENGTH_SHORT).show();
            Timber.e(context.getString(R.string.optibp_values_error));
            return null;
        }
        return bpEditText;
    }

    private int getRequestCode(boolean isRepeat) {
        return isRepeat ? OPTIBPCONSTANTS.OPTIBP_REPEAT_REQUEST_CODE : OPTIBPCONSTANTS.OPTIBP_REQUEST_CODE;
    }

    @SuppressWarnings("SameParameterValue")
    private boolean isRepeatMeasurement(BPFieldType systolicField, BPFieldType diastolicField) {
        return systolicField.getKey().contains("repeat") && diastolicField.getKey().contains("repeat");
    }

    protected String getInputJsonString(Context context, JSONObject jsonObject, WidgetArgs widgetArgs) throws JSONException {
        if (!jsonObject.has(OPTIBPCONSTANTS.OPTIBP_KEY_DATA)) {
            throw new JSONException(context.getString(R.string.missing_client_info));
        }
        JSONObject optiBPData = jsonObject.getJSONObject(OPTIBPCONSTANTS.OPTIBP_KEY_DATA);
        if (!optiBPData.has(OPTIBPCONSTANTS.OPTIBP_KEY_CLIENT_ID)
                || !optiBPData.has(OPTIBPCONSTANTS.OPTIBP_KEY_CLIENT_OPENSRP_ID)) {
            throw new JSONException(context.getString(R.string.missing_client_info));
        }
        if (TextUtils.isEmpty(optiBPData.getString(OPTIBPCONSTANTS.OPTIBP_KEY_CLIENT_ID))
                || TextUtils.isEmpty(optiBPData.getString(OPTIBPCONSTANTS.OPTIBP_KEY_CLIENT_OPENSRP_ID))) {
            throw new JSONException(context.getString(R.string.missing_client_info));
        }
        JSONArray optiBPCalibrationData = getCalibrationData(widgetArgs);
        if (optiBPCalibrationData != null) {
            optiBPData.put(OPTIBPCONSTANTS.OPTIBP_KEY_CALIBRATION, optiBPCalibrationData);
        }
        return optiBPData.toString();
    }

    private JSONArray getCalibrationData(WidgetArgs widgetArgs) {
        try {
            JSONObject calibrationData = FormUtils.getFieldFromForm(widgetArgs.getFormFragment().getJsonApi().getmJSONObject(), OPTIBPCONSTANTS.OPTIBP_KEY_CALIBRATION_DATA);
            if (calibrationData != null && StringUtils.isNotBlank(calibrationData.toString())) {
                String valueString = calibrationData.optString(VALUE);
                if (StringUtils.isBlank(valueString)) {
                    return null;
                }
                DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                df.setTimeZone(TimeZone.getTimeZone(ZoneId.of("Africa/Nairobi")));
                JSONObject heightObject = getSingleStepJsonObject(widgetArgs, STEP1, OPTIBPCONSTANTS.HEIGHT);
                JSONObject pregestWeight = getSingleStepJsonObject(widgetArgs, STEP1, OPTIBPCONSTANTS.CURRENTWEIGHT);
                if (heightObject != null && pregestWeight != null) {
                    JSONArray calibrationArray = new JSONArray();
                    JSONObject calibrationObject = new JSONObject();
                    JSONObject comperativesObject = new JSONObject();
                    calibrationObject.put(OPTIBPCONSTANTS.DATE, df.format(new Date()));
                    calibrationObject.put(OPTIBPCONSTANTS.VERSION, 1);
                    calibrationObject.put(OPTIBPCONSTANTS.MODEL, Build.MODEL);
                    calibrationObject.put(OPTIBPCONSTANTS.HEIGHT, Integer.parseInt(heightObject.optString(VALUE)));
                    calibrationObject.put(OPTIBPCONSTANTS.WEIGHT, Integer.parseInt(pregestWeight.optString(VALUE)));
                    JSONArray retrievedCalibrationData = new JSONArray(valueString);
                    comperativesObject.put(OPTIBPCONSTANTS.COMPERATIVES, retrievedCalibrationData);
                    calibrationArray.put(calibrationObject);
                    return calibrationArray;
                }
            }
        } catch (JSONException e) {
            Timber.e(e);
        }
        return null;
    }

    private static JSONObject getSingleStepJsonObject(WidgetArgs widgetArgs, String stepName, String key) {
        JSONArray jsonArray = widgetArgs.getFormFragment().getJsonApi().getStep(stepName).optJSONArray(FIELDS);
        return Utils.getJsonObjectFromJsonArray(key, jsonArray);

    }

    private String getLabelText(Context context, JSONObject jsonObject) throws JSONException {
        if (!jsonObject.has(LABEL)) {
            return context.getString(R.string.optibp_label);
        }
        return jsonObject.getString(LABEL);
    }

    @SuppressLint("InflateParams")
    public LinearLayout getRootLayout(Context context) {
        return (LinearLayout) LayoutInflater.from(context).inflate(R.layout.native_form_item_optibp_widget, null);
    }

    @Override
    public @NotNull Set<String> getCustomTranslatableWidgetFields() {
        Set<String> customTranslatableWidgetFields = new HashSet<>();
        customTranslatableWidgetFields.add(LABEL);
        return customTranslatableWidgetFields;
    }

    private void attachRefreshLogic(Context context, JSONObject jsonObject, View view) {
        String relevance = jsonObject.optString(RELEVANCE);
        String constraints = jsonObject.optString(CONSTRAINTS);
        String calculation = jsonObject.optString(CALCULATION);

        if (StringUtils.isNotBlank(relevance) && context instanceof JsonApi) {
            view.setTag(R.id.relevance, relevance);
            ((JsonApi) context).addSkipLogicView(view);
        }

        if (StringUtils.isNotBlank(constraints) && context instanceof JsonApi) {
            view.setTag(R.id.constraints, constraints);
            ((JsonApi) context).addConstrainedView(view);
        }

        if (StringUtils.isNotBlank(calculation) && context instanceof JsonApi) {
            view.setTag(R.id.calculation, calculation);
            ((JsonApi) context).addCalculationLogicView(view);
        }
    }

        protected enum BPFieldType {
        DIASTOLIC_BP("bp_diastolic"), SYSTOLIC_BP("bp_systolic");  // TODO -> Add these KEYS to explicit documentation

        private String key;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        BPFieldType(String key) {
            this.key = key;
        }
    }
}

