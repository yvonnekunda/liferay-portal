/**
 * Copyright (c) 2000-2012 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portlet.dynamicdatamapping.util;

import com.liferay.portal.kernel.io.unsync.UnsyncStringWriter;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.template.Template;
import com.liferay.portal.kernel.template.TemplateContextType;
import com.liferay.portal.kernel.template.TemplateManager;
import com.liferay.portal.kernel.template.TemplateManagerUtil;
import com.liferay.portal.kernel.template.TemplateResource;
import com.liferay.portal.kernel.template.TemplateResourceLoaderUtil;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.CharPool;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.LocalizationUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.xml.Attribute;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.DocumentException;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portlet.dynamicdatamapping.model.DDMStructure;
import com.liferay.portlet.dynamicdatamapping.model.DDMTemplate;
import com.liferay.portlet.dynamicdatamapping.model.DDMTemplateConstants;
import com.liferay.portlet.dynamicdatamapping.storage.Fields;
import com.liferay.util.freemarker.FreeMarkerTaglibFactoryUtil;

import freemarker.ext.servlet.HttpRequestHashModel;
import freemarker.ext.servlet.ServletContextHashModel;

import freemarker.template.ObjectWrapper;
import freemarker.template.TemplateHashModel;

import java.io.IOException;
import java.io.Writer;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.GenericServlet;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.PageContext;

/**
 * @author Bruno Basto
 * @author Eduardo Lundgren
 * @author Brian Wing Shun Chan
 */
public class DDMXSDImpl implements DDMXSD {

	public String getHTML(
			PageContext pageContext, DDMStructure ddmStructure, Fields fields,
			String namespace, boolean readOnly, Locale locale)
		throws Exception {

		return getHTML(
			pageContext, ddmStructure.getXsd(), fields, namespace, readOnly,
			locale);
	}

	public String getHTML(
			PageContext pageContext, DDMTemplate ddmTemplate, Fields fields,
			String namespace, boolean readOnly, Locale locale)
		throws Exception {

		return getHTML(
			pageContext, ddmTemplate.getScript(), fields, namespace,
			ddmTemplate.getMode(), readOnly, locale);
	}

	public String getHTML(
			PageContext pageContext, Element element, Fields fields,
			Locale locale)
		throws Exception {

		return getHTML(
			pageContext, element, fields, StringPool.BLANK, null, false,
			locale);
	}

	public String getHTML(
			PageContext pageContext, Element element, Fields fields,
			String namespace, String mode, boolean readOnly, Locale locale)
		throws Exception {

		StringBundler sb = new StringBundler();

		HttpServletRequest request =
			(HttpServletRequest)pageContext.getRequest();

		String portletId = PortalUtil.getPortletId(request);

		String portletNamespace = PortalUtil.getPortletNamespace(portletId);

		List<Element> dynamicElementElements = element.elements(
			"dynamic-element");

		for (Element dynamicElementElement : dynamicElementElements) {
			Map<String, Object> freeMarkerContext = getFreeMarkerContext(
				dynamicElementElement, locale);

			freeMarkerContext.put("portletNamespace", portletNamespace);
			freeMarkerContext.put("namespace", namespace);

			if (fields != null) {
				freeMarkerContext.put("fields", fields);
			}

			Map<String, Object> field =
				(Map<String, Object>)freeMarkerContext.get("fieldStructure");

			String childrenHTML = getHTML(
				pageContext, dynamicElementElement, fields, namespace, mode,
				readOnly, locale);

			field.put("children", childrenHTML);

			String fieldNamespace = dynamicElementElement.attributeValue(
				"fieldNamespace", _DEFAULT_NAMESPACE);

			String defaultResourcePath = _TPL_DEFAULT_PATH;

			boolean fieldReadOnly = GetterUtil.getBoolean(
				field.get("readOnly"));

			if ((fieldReadOnly && Validator.isNotNull(mode) &&
				 mode.equalsIgnoreCase(
					DDMTemplateConstants.TEMPLATE_MODE_EDIT)) || readOnly) {

				fieldNamespace = _DEFAULT_READ_ONLY_NAMESPACE;

				defaultResourcePath = _TPL_DEFAULT_READ_ONLY_PATH;
			}

			String type = dynamicElementElement.attributeValue("type");

			String templateName = StringUtil.replaceFirst(
				type, fieldNamespace.concat(StringPool.DASH), StringPool.BLANK);

			StringBundler resourcePath = new StringBundler(5);

			resourcePath.append(_TPL_PATH);
			resourcePath.append(fieldNamespace.toLowerCase());
			resourcePath.append(CharPool.SLASH);
			resourcePath.append(templateName);
			resourcePath.append(_TPL_EXT);

			String resource = resourcePath.toString();

			if (!TemplateResourceLoaderUtil.hasTemplateResource(
					TemplateManager.FREEMARKER, resource)) {

				resource = defaultResourcePath;
			}

			TemplateResource templateResource =
				TemplateResourceLoaderUtil.getTemplateResource(
					TemplateManager.FREEMARKER, resource);

			Template template = TemplateManagerUtil.getTemplate(
				TemplateManager.FREEMARKER, templateResource,
				TemplateContextType.STANDARD);

			for (Map.Entry<String, Object> entry :
					freeMarkerContext.entrySet()) {

				template.put(entry.getKey(), entry.getValue());
			}

			sb.append(processFTL(pageContext, template));
		}

		return sb.toString();
	}

	public String getHTML(
			PageContext pageContext, Element element, Locale locale)
		throws Exception {

		return getHTML(pageContext, element, null, locale);
	}

	public String getHTML(
			PageContext pageContext, String xml, Fields fields, Locale locale)
		throws Exception {

		return getHTML(pageContext, xml, fields, StringPool.BLANK, locale);
	}

	public String getHTML(
			PageContext pageContext, String xml, Fields fields,
			String namespace, boolean readOnly, Locale locale)
		throws Exception {

		return getHTML(
			pageContext, xml, fields, namespace, null, readOnly, locale);
	}

	public String getHTML(
			PageContext pageContext, String xml, Fields fields,
			String namespace, Locale locale)
		throws Exception {

		return getHTML(pageContext, xml, fields, namespace, false, locale);
	}

	public String getHTML(
			PageContext pageContext, String xml, Fields fields,
			String namespace, String mode, boolean readOnly, Locale locale)
		throws Exception {

		Document document = SAXReaderUtil.read(xml);

		return getHTML(
			pageContext, document.getRootElement(), fields, namespace, mode,
			readOnly, locale);
	}

	public String getHTML(PageContext pageContext, String xml, Locale locale)
		throws Exception {

		return getHTML(pageContext, xml, null, locale);
	}

	public JSONArray getJSONArray(Document document) throws JSONException {
		return getJSONArray(document.getRootElement());
	}

	public JSONArray getJSONArray(Element element) throws JSONException {
		JSONArray jsonArray = JSONFactoryUtil.createJSONArray();

		Document document = element.getDocument();

		String defaultLocale = LocalizationUtil.getDefaultLocale(
			document.asXML());

		List<Element> dynamicElementElements = element.elements(
			"dynamic-element");

		for (Element dynamicElementElement : dynamicElementElements) {
			JSONObject jsonObject = JSONFactoryUtil.createJSONObject();
			JSONObject localizationMapJSONObject =
				JSONFactoryUtil.createJSONObject();

			for (Attribute attribute : dynamicElementElement.attributes()) {
				jsonObject.put(attribute.getName(), attribute.getValue());
			}

			jsonObject.put("id", dynamicElementElement.attributeValue("name"));

			String type = jsonObject.getString("type");

			List<Element> metadataElements = dynamicElementElement.elements(
				"meta-data");

			for (Element metadataElement : metadataElements) {
				if (metadataElement == null) {
					continue;
				}

				String locale = metadataElement.attributeValue("locale");

				JSONObject localeMap = JSONFactoryUtil.createJSONObject();

				for (Element metadataEntryElement :
						metadataElement.elements()) {

					String attributeName = metadataEntryElement.attributeValue(
						"name");
					String attributeValue = metadataEntryElement.getText();

					putMetadataValue(
						localeMap, attributeName, attributeValue, type);

					if (defaultLocale.equals(locale)) {
						putMetadataValue(
							jsonObject, attributeName, attributeValue, type);
					}
				}

				localizationMapJSONObject.put(locale, localeMap);
			}

			jsonObject.put("localizationMap", localizationMapJSONObject);

			JSONArray hiddenAttributesJSONArray =
				JSONFactoryUtil.createJSONArray();

			if (type.equals(DDMImpl.TYPE_CHECKBOX)) {
				hiddenAttributesJSONArray.put("required");
			}

			if (type.equals(DDMImpl.TYPE_DDM_FILEUPLOAD)) {
				hiddenAttributesJSONArray.put("predefinedValue");
			}

			hiddenAttributesJSONArray.put("readOnly");

			jsonObject.put("hiddenAttributes", hiddenAttributesJSONArray);

			String key = "fields";

			if (type.equals(DDMImpl.TYPE_RADIO) ||
				type.equals(DDMImpl.TYPE_SELECT)) {

				key = "options";
			}

			jsonObject.put(key, getJSONArray(dynamicElementElement));

			jsonArray.put(jsonObject);
		}

		return jsonArray;
	}

	public JSONArray getJSONArray(String xml)
		throws DocumentException, JSONException {

		return getJSONArray(SAXReaderUtil.read(xml));
	}

	protected Map<String, Object> getFieldContext(
		Element dynamicElementElement, Locale locale) {

		Document document = dynamicElementElement.getDocument();

		String[] availableLocales = LocalizationUtil.getAvailableLocales(
			document.asXML());

		String defaultLanguageId = LocalizationUtil.getDefaultLocale(
			document.asXML());

		String languageId = LocaleUtil.toLanguageId(locale);

		if (!ArrayUtil.contains(availableLocales, languageId)) {
			languageId = defaultLanguageId;
		}

		Element metadataElement =
			(Element)dynamicElementElement.selectSingleNode(
				"meta-data[@locale='" + languageId + "']");

		Map<String, Object> field = new HashMap<String, Object>();

		if (metadataElement != null) {
			for (Element metadataEntry : metadataElement.elements()) {
				field.put(
					metadataEntry.attributeValue("name"),
					metadataEntry.getText());
			}
		}

		for (Attribute attribute : dynamicElementElement.attributes()) {
			field.put(attribute.getName(), attribute.getValue());
		}

		return field;
	}

	protected Map<String, Object> getFreeMarkerContext(
		Element dynamicElementElement, Locale locale) {

		Map<String, Object> freeMarkerContext = new HashMap<String, Object>();

		Map<String, Object> fieldContext = getFieldContext(
			dynamicElementElement, locale);

		Map<String, Object> parentFieldContext = new HashMap<String, Object>();

		Element parentElement = dynamicElementElement.getParent();

		if (parentElement != null) {
			parentFieldContext = getFieldContext(parentElement, locale);
		}

		freeMarkerContext.put("fieldStructure", fieldContext);
		freeMarkerContext.put("parentFieldStructure", parentFieldContext);

		return freeMarkerContext;
	}

	/**
	 * @see com.liferay.taglib.util.ThemeUtil#includeFTL
	 */
	protected String processFTL(PageContext pageContext, Template template)
		throws Exception {

		HttpServletRequest request =
			(HttpServletRequest)pageContext.getRequest();

		// FreeMarker variables

		template.prepare(request);

		// Tag libraries

		HttpServletResponse response =
			(HttpServletResponse)pageContext.getResponse();

		Writer writer = new UnsyncStringWriter();

		// Portal JSP tag library factory

		TemplateHashModel portalTaglib =
			FreeMarkerTaglibFactoryUtil.createTaglibFactory(
				pageContext.getServletContext());

		template.put("PortalJspTagLibs", portalTaglib);

		// FreeMarker JSP tag library support

		final Servlet servlet = (Servlet)pageContext.getPage();

		GenericServlet genericServlet = null;

		if (servlet instanceof GenericServlet) {
			genericServlet = (GenericServlet)servlet;
		}
		else {
			genericServlet = new GenericServlet() {

				@Override
				public void service(
						ServletRequest servletRequest,
						ServletResponse servletResponse)
					throws ServletException, IOException {

					servlet.service(servletRequest, servletResponse);
				}

			};

			genericServlet.init(pageContext.getServletConfig());
		}

		ServletContextHashModel servletContextHashModel =
			new ServletContextHashModel(
				genericServlet, ObjectWrapper.DEFAULT_WRAPPER);

		template.put("Application", servletContextHashModel);

		HttpRequestHashModel httpRequestHashModel = new HttpRequestHashModel(
			request, response, ObjectWrapper.DEFAULT_WRAPPER);

		template.put("Request", httpRequestHashModel);

		// Merge templates

		template.processTemplate(writer);

		return writer.toString();
	}

	protected void putMetadataValue(
		JSONObject jsonObject, String attributeName, String attributeValue,
		String type) {

		if (type.equals(DDMImpl.TYPE_RADIO) ||
			type.equals(DDMImpl.TYPE_SELECT)) {

			if (attributeName.equals("predefinedValue")) {
				try {
					jsonObject.put(
						attributeName,
						JSONFactoryUtil.createJSONArray(attributeValue));
				}
				catch (Exception e) {
				}

				return;
			}
		}

		jsonObject.put(attributeName, attributeValue);
	}

	private static final String _DEFAULT_NAMESPACE = "alloy";

	private static final String _DEFAULT_READ_ONLY_NAMESPACE = "readonly";

	private static final String _TPL_DEFAULT_PATH =
		"com/liferay/portlet/dynamicdatamapping/dependencies/alloy/text.ftl";

	private static final String _TPL_DEFAULT_READ_ONLY_PATH =
		"com/liferay/portlet/dynamicdatamapping/dependencies/readonly/" +
			"default.ftl";

	private static final String _TPL_EXT = ".ftl";

	private static final String _TPL_PATH =
		"com/liferay/portlet/dynamicdatamapping/dependencies/";

}