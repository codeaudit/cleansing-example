/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/
package eu.stratosphere.sopremo.cleansing.examples;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import eu.stratosphere.sopremo.function.SopremoFunction2;
import eu.stratosphere.sopremo.function.SopremoFunction3;
import eu.stratosphere.sopremo.operator.Name;
import eu.stratosphere.sopremo.packages.BuiltinProvider;
import eu.stratosphere.sopremo.type.ArrayNode;
import eu.stratosphere.sopremo.type.IArrayNode;
import eu.stratosphere.sopremo.type.IJsonNode;
import eu.stratosphere.sopremo.type.NullNode;
import eu.stratosphere.sopremo.type.TextNode;

public class GovWildUDF implements BuiltinProvider {

	@Name(noun = "normalize_name")
	public static class NORMALIZE_NAME extends
			SopremoFunction2<TextNode, TextNode> {

		private DictionarySearch dict = new DictionarySearch();

		@Override
		protected IJsonNode call(TextNode fullName, TextNode officialTitlesPath) {
			if (fullName == null)
				return NullNode.getInstance();

			String[] nameAndAddition = this.splitName(fullName);

			String addition = "";
			StringBuilder nameAdd = new StringBuilder("");
			while (addition != null) {
				addition = this.dict.search(nameAndAddition[0],
						officialTitlesPath.toString(), 0, false, false);
				if (addition != null) {
					nameAdd.append(" ").append(addition);
					nameAndAddition[0] = nameAndAddition[0].replace(addition,
							"");
				}
			}

			String name = nameAndAddition[0];

			if (nameAndAddition.length > 1) {
				nameAdd.append(" ").append(nameAndAddition[1].trim());
			}

			IArrayNode<IJsonNode> result = this.normalizeName(name);

			result.add(TextNode.valueOf(nameAdd.toString().trim()));

			return result;
		}

		private String[] splitName(TextNode fullName) {
			String[] nameAndAddition = fullName.toString().split(",");

			if (nameAndAddition.length > 2) {
				for (int i = 2; i < nameAndAddition.length; i++) {
					nameAndAddition[1] += " " + nameAndAddition[i];
				}
				return Arrays.copyOf(nameAndAddition, 2);
			}

			return nameAndAddition;
		}

		private IArrayNode<IJsonNode> normalizeName(String name) {
			IArrayNode<IJsonNode> result = new ArrayNode<IJsonNode>(
					TextNode.EMPTY_STRING, TextNode.EMPTY_STRING,
					TextNode.EMPTY_STRING);
			if (name == null) {
				return result;
			}

			String[] nameParts = name.split(" ");

			String firstName = null;
			String middleName = null;
			String lastName = null;

			if (nameParts.length > 2) {
				middleName = "";
				for (int i = 1; i < nameParts.length - 1; i++) {
					middleName += " " + nameParts[i];
				}

				firstName = nameParts[0].trim();
				middleName = middleName.trim();
				lastName = nameParts[nameParts.length - 1].trim();
			} else if (nameParts.length == 2) {
				firstName = nameParts[0].trim();
				lastName = nameParts[1].trim();
			} else if (nameParts.length == 1) {
				lastName = nameParts[0].trim();
			}

			if (firstName != null)
				result.set(0, TextNode.valueOf(firstName));
			if (middleName != null)
				result.set(1, TextNode.valueOf(middleName));
			if (lastName != null)
				result.set(2, TextNode.valueOf(lastName));

			return result;
		}

	};

	private static class DictionarySearch {
		private String line, entry, found;

		private transient String dictionary;
		private transient File dictionaryFile;

		private int size, position;

		private int recordNr = 0;

		public String search(String searchStr, String dictionary, int where,
				boolean ignoreCase, boolean first) {
			if ((searchStr == null) || searchStr.equals("")) {
				return null;
			}
			if (this.dictionary == null || !this.dictionary.equals(dictionary)) {
				this.dictionaryFile = new File(dictionary);
				this.dictionary = dictionary;
			}
			BufferedReader br;
			try {
				br = new BufferedReader(new FileReader(this.dictionaryFile));
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
				return null;
			}

			this.found = null;
			this.size = 0;

			searchStr = format(searchStr, ignoreCase);

			try {
				while ((this.line = br.readLine()) != null) {
					this.entry = format(this.line, ignoreCase);

					this.position = where > 0 ? searchStr
							.lastIndexOf(this.entry) : searchStr
							.indexOf(this.entry);

					if (this.position < 0
							|| (where > 0 && (this.position + this.entry
									.length()) < searchStr.length())
							|| (where < 0 && this.position > 0)) {
						continue;
					}

					if (this.line.length() > this.size) {
						this.found = this.line;
						this.size = this.line.length();

						if (first && this.size > 0) {
							break;
						}
					}
				}
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}

			return this.found;
		}

		private String format(String input, boolean ignoreCase) {
			input = " " + input.trim() + " ";

			if (ignoreCase) {
				input = input.toLowerCase();
			}

			return input;
		}
	}
	
	public static DICTIONARY_REPLACEMENT DICTIONARY_REPLACEMENT = new DICTIONARY_REPLACEMENT();

	@Name(noun = "dict_replace")
	public static class DICTIONARY_REPLACEMENT extends
			SopremoFunction3<TextNode, IJsonNode, TextNode> {

		private String DICT_ENDING = ".dict";

		private String DEFAULT_DICTIONARY_PATH = System.getProperty("user.dir")
				+ "/resources/dictionaries/";

		private String KEY_VALUE_SEPARATOR = "\\|";

		private String VALUE_SEPARATOR = ";";

		@Override
		protected IJsonNode call(TextNode inputNode, IJsonNode dictionaryPath,
				TextNode dictNameNode) {
			Map<String, String> mappings = this
					.loadDictionary(
							(dictionaryPath instanceof NullNode) ? this.DEFAULT_DICTIONARY_PATH
									: dictionaryPath.toString(), dictNameNode
									.toString());
			String input = inputNode.toString();
			String result = input;
			String sub;

			if (mappings.containsKey(input)) {
				result = mappings.get(input);
			} else if (mappings.containsValue(input)) {
				result = input;
			} else if ((sub = this.mappingSimilarTo(input, mappings)) != null) {
				result = sub;
			} else {
				// for unknown entities
				result = input;
			}
			return TextNode.valueOf(result);
		}

		// we also map similar strings
		private String mappingSimilarTo(String input,
				Map<String, String> mappings) {
			for (String value : mappings.values()) {
				if (input.startsWith(value) || input.endsWith(value)) {
					return value;
				}
			}
			return null;
		}

		private Map<String, String> loadDictionary(String dictionaryPath,
				String dictionaryName) {
			Map<String, String> mappings = new HashMap<String, String>();
			if (dictionaryName == null || "".equals(dictionaryName)) {
				return mappings;
			}
			try {
				BufferedReader br = new BufferedReader(new FileReader(
						dictionaryPath + dictionaryName + this.DICT_ENDING));
				String line, key;
				String[] keyValues, values;

				try {
					while ((line = br.readLine()) != null) {
						keyValues = line.split(this.KEY_VALUE_SEPARATOR);
						key = keyValues[0];
						values = keyValues[1].split(this.VALUE_SEPARATOR);
						for (String value : values) {
							// we have to put the key-value pairs in reversed
							// order to allow multiple mappings from the
							// same shortcut (key) to different spellings
							// (values)
							mappings.put(value, key);
						}
					}
					br.close();
				} catch (IOException e) {
					e.printStackTrace();

					// if an error occurs we just return an empty map to allow
					// further execution of the meteor script
					return new HashMap<String, String>();
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();

				// if the specified dictionary doesn't exist we return the empty
				// map to allow further execution of the
				// meteor script
				return mappings;
			}
			return mappings;
		}
	};

}