/* 
 * Copyright 2009-2011 junithelper.org. 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
 * either express or implied. See the License for the specific language 
 * governing permissions and limitations under the License. 
 */
package org.junithelper.core.config.extension;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ExtConfigurationParserHandler extends DefaultHandler {

	private ExtConfiguration config;

	private ExtArg currentArg;

	private ExtArgPattern currentArgPattern;

	private ExtReturn currentReturn;

	private String tempValue;

	public ExtConfiguration getExtConfiguration() {
		return this.config;
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		tempValue = new String(ch, start, length);
	}

	@Override
	public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
		if (name.equals("junithelper-extension")) {
			config = new ExtConfiguration();
		} else if (name.equals("arg")) {
			if (attributes.getValue("class") != null) {
				String className = attributes.getValue("class");
				currentArg = new ExtArg(className);
				return;
			}
		} else if (name.equals("pattern")) {
			if (attributes.getValue("name") != null) {
				String patternName = attributes.getValue("name");
				currentArgPattern = new ExtArgPattern(currentArg, patternName);
				return;
			}
		} else if (name.equals("return")) {
			if (attributes.getValue("class") != null) {
				String className = attributes.getValue("class");
				currentReturn = new ExtReturn(className);
				return;
			}
		}
	}

	@Override
	public void endElement(String uri, String localName, String name) throws SAXException {
		if (name.equals("arg")) {
			config.extArgs.add(currentArg);
			currentArg = null;
		} else if (name.equals("pattern")) {
			currentArg.patterns.add(currentArgPattern);
			currentArgPattern = null;
		} else if (name.equals("return")) {
			config.extReturns.add(currentReturn);
			currentReturn = null;
		} else if (name.equals("import")) {
			currentArg.importList.add(tempValue);
		} else if (name.equals("pre-assign")) {
			currentArgPattern.preAssignCode = tempValue;
		} else if (name.equals("assign")) {
			currentArgPattern.assignCode = tempValue;
		} else if (name.equals("post-assign")) {
			currentArgPattern.postAssignCode = tempValue;
		} else if (name.equals("assert")) {
			currentReturn.asserts.add(tempValue);
		}
	}

}
