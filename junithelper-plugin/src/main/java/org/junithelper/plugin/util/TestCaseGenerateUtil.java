/* 
 * Copyright 2009-2010 junithelper.org. 
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
package org.junithelper.plugin.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.preference.IPreferenceStore;
import org.junithelper.plugin.Activator;
import org.junithelper.plugin.bean.ArgType;
import org.junithelper.plugin.bean.ClassInfo;
import org.junithelper.plugin.bean.ConstructorInfo;
import org.junithelper.plugin.bean.MethodInfo;
import org.junithelper.plugin.bean.MethodInfo.ExceptionInfo;
import org.junithelper.plugin.constant.Message;
import org.junithelper.plugin.constant.Preference;
import org.junithelper.plugin.constant.RegExp;
import org.junithelper.plugin.constant.StrConst;
import org.junithelper.plugin.exception.InvalidPreferenceException;
import org.junithelper.plugin.io.PropertiesLoader;
import org.junithelper.plugin.page.PreferenceLoader;

/**
 * TestCaseGenerateUtil
 * 
 * @author Kazuhiro Sera <seratch@gmail.com>
 * @version 1.0
 */
public final class TestCaseGenerateUtil {

	static IWorkspace workspace = null;

	static IWorkspaceRoot workspaceRoot = null;

	static {
		try {
			workspace = ResourcesPlugin.getWorkspace();
			workspaceRoot = workspace.getRoot();
			store = Activator.getDefault().getPreferenceStore();
		} catch (Exception e) {
		}
	}

	public static IPreferenceStore store;

	/**
	 * Get the information on the unimplemented test methods.
	 * 
	 * @param testTargetClassname
	 *            test target class name
	 * @param testTarget
	 *            test target class source code file
	 * @param testCase
	 *            test class source code file
	 * @return the information on test class with the unimplemented test methods
	 * @throws Exception
	 */
	public static ClassInfo getClassInfoWithUnimplementedTestMethods(
			String testTargetClassname, IFile testTarget, IFile testCase)
			throws Exception {
		PreferenceLoader pref = new PreferenceLoader(store);
		ClassInfo classInfo = new ClassInfo();
		List<MethodInfo> unimplementedMethodNames = new ArrayList<MethodInfo>();
		// enable public method test
		if (pref.isTestMethodGenEnabled) {
			ClassInfo expectedClassInfo = getTestClassInfoFromTargetClass(
					testTargetClassname, testTarget);
			List<MethodInfo> expectedMethods = expectedClassInfo.methods;
			ClassInfo actualClassInfo = getMethodNamesAlreadyExists(testCase);
			List<MethodInfo> actualMethods = actualClassInfo.methods;
			for (MethodInfo expected : expectedMethods) {
				if (!isAlreadyExistInActualTestCase(expected.methodName,
						actualMethods)) {
					unimplementedMethodNames.add(expected);
				}
			}
			// imported types
			if (pref.isTestMethodGenNotBlankEnabled) {
				List<String> notImportedList = new ArrayList<String>();
				List<String> expImportedList = expectedClassInfo.importList;
				List<String> actImportedList = actualClassInfo.importList;
				for (String expImported : expImportedList) {
					boolean found = false;
					for (String actImported : actImportedList) {
						if (expImported.equals(actImported)) {
							found = true;
							break;
						}
					}
					if (!found)
						notImportedList.add(expImported);
					else
						notImportedList.add("//" + expImported);
				}
				classInfo.importList = notImportedList;
				classInfo.importList = setupRequiredImports(pref,
						actImportedList, classInfo.importList);
			}
			// constructors
			// needed to generate in test method source code
			classInfo.constructors = expectedClassInfo.constructors;
		}
		classInfo.methods = unimplementedMethodNames;
		return classInfo;
	}

	/**
	 * Expected method name is already exist in the actual test case
	 * 
	 * @param expectedTestMethodName
	 *            expected test method name
	 * @param actualMethods
	 *            method names that are already exist
	 * @return Expected method name is already exist in the actual test case
	 */
	protected static boolean isAlreadyExistInActualTestCase(
			String expectedTestMethodName, List<MethodInfo> actualMethods) {
		boolean exist = false;
		for (MethodInfo actual : actualMethods) {
			String escapedExp = expectedTestMethodName
					.replaceFirst(StrConst.testMethodPrefix4Version3,
							StrConst.empty).replaceAll("\\$", "\\\\\\$")
					.replaceAll("[\\(\\)\\[\\]]", StrConst.empty);
			if (actual.testMethodName.matches(".*" + escapedExp + ".*")) {
				exist = true;
				break;
			}
		}
		return exist;
	}

	/**
	 * Get the meta information on the class.
	 * 
	 * @param javaFile
	 *            java source code file
	 * @return the meta information on the class
	 * @throws Exception
	 */
	public static ClassInfo getMethodNamesAlreadyExists(IFile javaFile)
			throws Exception {
		PreferenceLoader pref = new PreferenceLoader(store);
		ClassInfo classInfo = new ClassInfo();
		List<MethodInfo> methodStringInfos = new ArrayList<MethodInfo>();
		// enable public method test
		if (pref.isTestMethodGenEnabled) {
			InputStream is = null;
			InputStreamReader isr = null;
			BufferedReader br = null;
			try {
				is = FileResourceUtil.readFile(javaFile);
				// detect charset
				String encoding = FileResourceUtil.detectEncoding(javaFile);
				is = FileResourceUtil.readFile(javaFile);
				isr = new InputStreamReader(is, encoding);
				br = new BufferedReader(isr);
				StringBuilder tmpsb = new StringBuilder();
				String line = null;
				while ((line = br.readLine()) != null) {
					tmpsb.append(line + StrConst.space);
				}
				String targetClassSourceStr = tmpsb.toString();
				String[] targets = targetClassSourceStr.split("public");
				for (String target : targets) {
					target = target.replaceAll("\\s+?" + StrConst.comma,
							StrConst.comma).replaceAll(
							StrConst.comma + "\\s+?", StrConst.comma);
					target = target.replaceAll("<\\s+?", "<").replaceAll(
							"\\s+?>", ">");
					if (target.matches(RegExp.matchesMethod)) {
						Matcher matcher = RegExp.groupMethodPattern
								.matcher(target);
						if (matcher.find()) {
							MethodInfo each = new MethodInfo();
							// return type
							each.returnType.name = getType(matcher.group(1));
							each.returnType.nameInMethodName = getTypeAvailableInMethodName(matcher
									.group(1));
							// method name
							each.methodName = matcher.group(2);
							each.testMethodName = each.methodName;
							// arg types
							String args = matcher.group(3);
							String[] argArr = args.split(",");
							int argArrLen = argArr.length;
							for (int i = 0; i < argArrLen; i++) {
								ArgType argType = new ArgType();
								argType.name = getType(argArr[i]);
								argType.nameInMethodName = getTypeAvailableInMethodName(argArr[i]);
								each.argTypes.add(argType);
							}
							methodStringInfos.add(each);
						}
					}
				}
				// imported types
				if (pref.isTestMethodGenNotBlankEnabled) {
					if (methodStringInfos.size() <= 0
							|| methodStringInfos.get(0) == null)
						methodStringInfos.add(new MethodInfo());
					String[] importLines = targetClassSourceStr
							.split("import\\s+");
					for (String importLine : importLines) {
						importLine = importLine.replaceAll(StrConst.lineFeed,
								StrConst.empty);
						// not package or not comment
						if (!importLine.matches("\\s*?package\\s.+")
								&& !importLine.matches("/\\*.+")) {
							String importedPackage = importLine.split(";")[0];
							classInfo.importList.add(importedPackage);
						}
					}
				}
			} finally {
				FileResourceUtil.close(br);
				FileResourceUtil.close(isr);
				FileResourceUtil.close(is);
			}
		}
		classInfo.methods = methodStringInfos;
		return classInfo;
	}

	/**
	 * Get list of constructor meta info.
	 * 
	 * @param pref
	 *            preference loader object
	 * @param classInfo
	 *            class meta info
	 * @param targetClassSourceStr
	 *            target source code string value
	 * @return constructor info list
	 */
	protected static List<ConstructorInfo> getConstructors(
			PreferenceLoader pref, ClassInfo classInfo,
			String targetClassSourceStr) {
		// get constructors
		List<String> targetConstructors = SourceCodeParseUtil
				.getTargetConstructors(classInfo.name, targetClassSourceStr,
						true, true, true);
		List<ConstructorInfo> constructors = new ArrayList<ConstructorInfo>();
		for (String target : targetConstructors) {
			String groupConstructor = RegExp.wsAsteriskMax + classInfo.name
					+ "\\(([^\\)]*?)\\)" + RegExp.wsAsteriskMax
					+ "(throws .+)*.*?" + RegExp.wsAsteriskMax + "\\{.*";
			Matcher constructorMatcher = Pattern.compile(groupConstructor)
					.matcher(target);
			if (constructorMatcher.find()) {
				ConstructorInfo each = new ConstructorInfo();
				String args = constructorMatcher.group(1);
				// prepare to get generics
				String[] tmpArr = args.split(StrConst.comma);
				int tmpArrLen = tmpArr.length;
				List<String> tmpArrList = new ArrayList<String>();
				String buf = StrConst.empty;
				for (int i = 0; i < tmpArrLen; i++) {
					String element = tmpArr[i].trim();
					// ex. List<String>
					if (element.matches(".+?<.+?>.+")) {
						tmpArrList.add(element);
						continue;
					}
					// ex. Map<String
					if (element.matches(".+?<.+")) {
						buf += element;
						continue;
					}
					// ex. (Map<String,) Object>
					if (element.matches(".+?>.+")) {
						String result = buf + StrConst.comma + element;
						tmpArrList.add(result);
						buf = StrConst.empty;
						continue;
					}
					if (!buf.equals(StrConst.empty)) {
						buf += StrConst.comma + element;
						continue;
					}
					tmpArrList.add(element);
				}
				String[] argArr = tmpArrList.toArray(new String[0]);
				if (pref.isTestMethodGenNotBlankEnabled) {
					int argArrLen = argArr.length;
					for (int i = 0; i < argArrLen; i++) {
						ArgType argType = new ArgType();
						String argTypeFull = argArr[i];
						Matcher toGenericsMatcher = Pattern.compile(
								RegExp.genericsGroup).matcher(argTypeFull);
						while (toGenericsMatcher.find()) {
							String[] generics = toGenericsMatcher.group()
									.replaceAll("<", StrConst.empty)
									.replaceAll(">", StrConst.empty)
									.split(StrConst.comma);
							// convert to java.lang.Object if self
							// class is included
							for (String generic : generics) {
								generic = getClassInSourceCode(generic,
										StrConst.empty, classInfo.importList);
								argType.generics.add(generic);
							}
						}
						String argTypeStr = argTypeFull.replaceAll(
								RegExp.generics, StrConst.empty);
						argType.name = getType(argTypeStr);
						argType.nameInMethodName = getTypeAvailableInMethodName(argTypeStr);
						each.argTypes.add(argType);
						Matcher nameMatcher = RegExp.groupMethodArgNamePattern
								.matcher(argTypeFull);
						if (nameMatcher.find()) {
							each.argNames.add(nameMatcher.group(1));
						} else {
							each.argNames.add("constructorArg" + i);
						}
					}
				}
				constructors.add(each);
			}
		}
		return constructors;
	}

	/**
	 * Get the information on the test methods corresponded the developing
	 * public methods.
	 * 
	 * @param testTargetClassname
	 *            test target class name
	 * @param javaFile
	 *            Java source code file
	 * @return the information on the test case class
	 * @throws Exception
	 */
	public static ClassInfo getTestClassInfoFromTargetClass(
			String testTargetClassname, IFile javaFile)
			throws InvalidPreferenceException, IOException {
		PreferenceLoader pref = new PreferenceLoader(store);
		ClassInfo classInfo = new ClassInfo();
		classInfo.name = testTargetClassname;
		List<ConstructorInfo> constructors = new ArrayList<ConstructorInfo>();
		List<MethodInfo> testMethods = new ArrayList<MethodInfo>();
		InputStream is = null;
		InputStreamReader isr = null;
		BufferedReader br = null;
		try {
			// detect charset
			String encoding = FileResourceUtil.detectEncoding(javaFile);
			// read test target class source code string
			is = FileResourceUtil.readFile(javaFile);
			isr = new InputStreamReader(is, encoding);
			br = new BufferedReader(isr);
			StringBuilder tmpsb = new StringBuilder();
			String line = null;
			while ((line = br.readLine()) != null) {
				line += StrConst.lineFeed;
				tmpsb.append(SourceCodeParseUtil.trimLineComments(line));
				tmpsb.append(StrConst.space);
			}
			// source code string (inner class methods are excluded)
			String targetClassSourceStrWithoutCommentsAndAnnotaions = SourceCodeParseUtil
					.trimAllAnnotations(SourceCodeParseUtil
							.trimAllComments(tmpsb.toString()));
			String targetClassSourceStr = SourceCodeParseUtil
					.trimInsideOfBraces(targetClassSourceStrWithoutCommentsAndAnnotaions);
			// get imported types
			if (pref.isTestMethodGenNotBlankEnabled) {
				if (testMethods.size() <= 0 || testMethods.get(0) == null)
					testMethods.add(new MethodInfo());
				String[] importLines = targetClassSourceStr.split("import\\s+");
				for (String importLine : importLines) {
					// not package or not comment
					importLine = importLine.replaceAll(StrConst.lineFeed,
							StrConst.empty);
					if (!importLine.matches("\\s*?package\\s.+")
							&& !importLine.matches("/\\*.+")) {
						String importedPackage = importLine.split(";")[0];
						classInfo.importList.add(importedPackage);
					}
				}
				classInfo.importList = setupRequiredImports(pref,
						new ArrayList<String>(), classInfo.importList);
			}
			// get constructors
			constructors = getConstructors(pref, classInfo,
					targetClassSourceStr);

			// get test target methods
			List<String> targets = SourceCodeParseUtil.getTargetMethods(
					targetClassSourceStr, pref.isTestMethodGenIncludePublic,
					pref.isTestMethodGenIncludeProtected,
					pref.isTestMethodGenIncludePackageLocal);
			for (String target : targets) {
				Matcher matcher = RegExp.groupMethodPattern.matcher(target);
				if (matcher.find()) {
					MethodInfo each = new MethodInfo();
					// return type
					if (pref.isTestMethodGenNotBlankEnabled
							|| pref.isTestMethodGenReturnEnabled) {
						String returnTypeFull = getType(matcher.group(1));
						// get generics
						Matcher toGenericsMatcher = Pattern.compile(
								RegExp.genericsGroup).matcher(returnTypeFull);
						while (toGenericsMatcher.find()) {
							String[] generics = toGenericsMatcher.group()
									.replaceAll("<", StrConst.empty)
									.replaceAll(">", StrConst.empty)
									.split(StrConst.comma);
							// convert to java.lang.Object if self
							// class is included
							for (String generic : generics) {
								generic = getClassInSourceCode(generic,
										StrConst.empty, classInfo.importList);
								each.returnType.generics.add(generic);
							}
						}
						each.returnType.name = returnTypeFull.replace(
								RegExp.generics, StrConst.empty);
						each.returnType.nameInMethodName = getTypeAvailableInMethodName(each.returnType.name);
					}
					// method name
					each.methodName = matcher.group(2);
					// arg types
					String args = matcher.group(3);
					// prepare to get generics
					String[] tmpArr = args.split(StrConst.comma);
					int tmpArrLen = tmpArr.length;
					List<String> tmpArrList = new ArrayList<String>();
					String buf = StrConst.empty;
					for (int i = 0; i < tmpArrLen; i++) {
						String element = tmpArr[i].trim();
						// ex. List<String>
						if (element.matches(".+?<.+?>.+")) {
							tmpArrList.add(element);
							continue;
						}
						// ex. Map<String
						if (element.matches(".+?<.+")) {
							buf += element;
							continue;
						}
						// ex. (Map<String,) Object>
						if (element.matches(".+?>.+")) {
							String result = buf + StrConst.comma + element;
							tmpArrList.add(result);
							buf = StrConst.empty;
							continue;
						}
						if (!buf.equals(StrConst.empty)) {
							buf += StrConst.comma + element;
							continue;
						}
						tmpArrList.add(element);
					}
					String[] argArr = tmpArrList.toArray(new String[0]);
					if (pref.isTestMethodGenNotBlankEnabled
							|| pref.isTestMethodGenArgsEnabled) {
						int argArrLen = argArr.length;
						for (int i = 0; i < argArrLen; i++) {
							ArgType argType = new ArgType();
							String argTypeFull = argArr[i];
							Matcher toGenericsMatcher = Pattern.compile(
									RegExp.genericsGroup).matcher(argTypeFull);
							while (toGenericsMatcher.find()) {
								String[] generics = toGenericsMatcher.group()
										.replaceAll("<", StrConst.empty)
										.replaceAll(">", StrConst.empty)
										.split(StrConst.comma);
								// convert to java.lang.Object if self
								// class is included
								for (String generic : generics) {
									generic = getClassInSourceCode(generic,
											StrConst.empty,
											classInfo.importList);
									argType.generics.add(generic);
								}
							}
							String argTypeStr = argTypeFull.replaceAll(
									RegExp.generics, StrConst.empty);
							argType.name = getType(argTypeStr);
							argType.nameInMethodName = getTypeAvailableInMethodName(argTypeStr);
							each.argTypes.add(argType);
							Matcher nameMatcher = RegExp.groupMethodArgNamePattern
									.matcher(argTypeFull);
							if (nameMatcher.find()) {
								each.argNames.add(nameMatcher.group(1));
							} else {
								each.argNames.add("arg" + i);
							}
						}
					}
					// exlucdes accessors
					if (pref.isTestMethodGenExecludeAccessors) {
						String fieldName = null;
						String fieldType = null;
						if (each.methodName.matches("^set.+")) {
							// target field name
							fieldName = each.methodName.substring(3);
							if (each.argTypes.size() > 0) {
								fieldType = each.argTypes.get(0).name;
							}
						} else if (each.methodName.matches("^get.+")) {
							// target field name
							fieldName = each.methodName.substring(3);
							fieldType = each.returnType.name;
						} else if (each.methodName.matches("^is.+")) {
							// target field name
							fieldName = each.methodName.substring(2);
							fieldType = each.returnType.name;
						}
						if (fieldName != null) {
							fieldName = fieldName.substring(0, 1).toLowerCase()
									+ fieldName.substring(1);
							fieldType = fieldType.replaceAll("\\[", "\\\\[")
									.replaceAll("\\]", "\\\\]")
									.replaceAll(",", "\\\\s*,\\\\s*");
							String searchRegexp = ".*?private\\s+" + fieldType
									+ "(" + RegExp.generics + ")*"
									+ RegExp.wsPlusMax + fieldName + ".+";
							if (targetClassSourceStr.replaceAll(RegExp.crlf,
									StrConst.empty).matches(searchRegexp)) {
								continue;
							}
						}
					}
					String prefix = pref.isJUnitVersion3 ? StrConst.testMethodPrefix4Version3
							+ pref.testMethodDelimiter
							: StrConst.empty;
					each.testMethodName = prefix + each.methodName;
					// add arg types
					if (pref.isTestMethodGenArgsEnabled) {
						each.testMethodName += pref.testMethodDelimiter
								+ pref.testMethodArgsPrefix;
						if (each.argTypes.size() == 0) {
							each.testMethodName += pref.testMethodArgsDelimiter;
						}
						for (ArgType argType : each.argTypes) {
							each.testMethodName += pref.testMethodArgsDelimiter
									+ argType.nameInMethodName;
						}
					}
					// add return type
					if (pref.isTestMethodGenReturnEnabled) {
						each.testMethodName += pref.testMethodDelimiter
								+ pref.testMethodReturnPrefix
								+ pref.testMethodReturnDelimiter
								+ each.returnType.nameInMethodName;
					}
					// static or instance method
					if (target.matches(RegExp.matchesStaticMethod)) {
						each.isStatic = true;
					}
					testMethods.add(each);

					// testing exception patterns
					if (pref.isTestMethodGenExceptions) {
						String throwsExceptions = matcher.group(4);
						if (throwsExceptions != null) {
							String[] exceptions = throwsExceptions
									.replaceAll("throws" + RegExp.wsPlusMax,
											StrConst.empty).split(
											StrConst.comma);
							for (String exp : exceptions) {
								exp = exp.trim();
								MethodInfo expTest = ObjectUtil.deepCopy(each);
								expTest.testingTargetException = new ExceptionInfo();
								expTest.testingTargetException.name = exp;
								expTest.testingTargetException.nameInMethodName = TestCaseGenerateUtil
										.getTypeAvailableInMethodName(exp);
								expTest.testMethodName = expTest.testMethodName
										+ pref.testMethodDelimiter
										+ pref.testMethodExceptionPrefix
										+ pref.testMethodExceptionDelimiter
										+ expTest.testingTargetException.nameInMethodName;
								testMethods.add(expTest);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			// TODO
		} finally {
			FileResourceUtil.close(br);
			FileResourceUtil.close(isr);
			FileResourceUtil.close(is);
		}
		classInfo.constructors = constructors;
		classInfo.methods = testMethods;
		return classInfo;
	}

	/**
	 * Get the source code of the area that define required instance fields for
	 * mocked args by JMockit.
	 * 
	 * @param testMethod
	 *            test method meta info
	 * @param testClassinfo
	 *            test case class meta info
	 * @param testTargetClassname
	 *            test target class name
	 * @return source code string value
	 */
	public static String getRequiredInstanceFieldsSourceForJMockitTestMethod(
			MethodInfo testMethod, ClassInfo testClassInfo,
			String testTargetClassname) {
		if (!new PreferenceLoader(store).isTestMethodGenEnabledSupportJMockit) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		List<ArgType> argTypes = testMethod.argTypes;
		List<String> argNames = testMethod.argNames;
		int argTypesLen = argTypes.size();
		if (argTypesLen > 0 && argTypes.get(0).name != null
				&& !argTypes.get(0).name.equals(StrConst.empty)) {
			for (int i = 0; i < argTypesLen; i++) {
				ArgType argType = argTypes.get(i);
				// flexible length args
				if (argType.name.matches(".+\\.\\.\\."))
					argType.name = argType.name.replaceAll("\\.\\.\\.", "[]");
				String argTypeName = getClassInSourceCode(argType.name,
						testTargetClassname, testClassInfo.importList);
				// generics
				boolean isNeedToMock = MockGenUtil.isMockableClassName(
						argTypeName, testClassInfo.importList);
				if (!isNeedToMock) {
					continue;
				}
				sb.append(StrConst.tab);
				sb.append("@Mocked ");
				sb.append(StrConst.carriageReturn);
				sb.append(StrConst.lineFeed);
				sb.append(StrConst.tab);
				sb.append(argTypeName);
				// add generics
				if (argType.generics.size() > 0) {
					sb.append("<");
					sb.append(argType.generics.get(0));
					if (argType.generics.size() > 1) {
						for (int j = 1; j < argType.generics.size(); j++) {
							sb.append(StrConst.comma);
							sb.append(argType.generics.get(j));
						}
					}
					sb.append(">");
				}
				sb.append(" ");
				sb.append(testMethod.testMethodName);
				sb.append("_");
				String argName = argNames.get(i);
				if (argName == null || argName.length() == 0) {
					argName = "arg" + i;
				}
				sb.append(argName);
				sb.append(";");
				sb.append(StrConst.carriageReturn);
				sb.append(StrConst.lineFeed);
			}
		}
		if (sb.toString().length() > 0) {
			sb.append(StrConst.carriageReturn);
			sb.append(StrConst.lineFeed);
		}
		return sb.toString();
	}

	/**
	 * Get sample implementation source code of the test methods.
	 * 
	 * @param testMethod
	 *            test method meta info
	 * @param testClassinfo
	 *            test case class meta info
	 * @param testTargetClassname
	 *            test target class name
	 * @return sample implementation source code
	 */
	public static String getNotBlankTestMethodSource(MethodInfo testMethod,
			ClassInfo testClassinfo, String testTargetClassname) {
		PreferenceLoader pref = new PreferenceLoader(store);
		PropertiesLoader props = new PropertiesLoader(
				store.getString(Preference.lang));
		StringBuilder sb = new StringBuilder();
		String CRLF = StrConst.carriageReturn + StrConst.lineFeed;
		if (pref.isTestMethodGenEnabledSupportJMock2) {
			if (pref.isUsingJUnitHelperRuntime) {
				// JUnit 4.x does not have the field.
				if (pref.isJUnitVersion4) {
					sb.append(StrConst.tab);
					sb.append(StrConst.tab);
					sb.append("Mockery jmock2 = JMock2Util.getNewInstance();");
					sb.append(CRLF);
				}
			} else {
				sb.append(StrConst.tab);
				sb.append(StrConst.tab);
				sb.append("Mockery context = new Mockery(){{");
				sb.append(CRLF);
				sb.append(StrConst.tab);
				sb.append(StrConst.tab);
				sb.append(StrConst.tab);
				sb.append("setImposteriser(ClassImposteriser.INSTANCE);");
				sb.append(CRLF);
				sb.append(StrConst.tab);
				sb.append(StrConst.tab);
				sb.append("}};");
				sb.append(CRLF);
			}
		}
		if (pref.isTestMethodGenEnabledSupportEasyMock) {
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
			sb.append("IMocksControl mocks = EasyMock.createControl();");
			sb.append(CRLF);
		}
		sb.append(StrConst.tab);
		sb.append(StrConst.tab);
		String returnTypeName = testMethod.returnType.name;
		Object returnDefaultValue = null;
		if (!returnTypeName.equals("void")) {
			returnTypeName = returnTypeName.replaceAll(RegExp.generics,
					StrConst.empty);
			returnTypeName = getClassInSourceCode(returnTypeName,
					testTargetClassname, testClassinfo.importList);
			List<String> generics = testMethod.returnType.generics;
			int genericsLen = generics.size();
			if (genericsLen > 0) {
				returnTypeName += "<" + generics.get(0);
				for (int i = 1; i < genericsLen; i++) {
					returnTypeName += StrConst.comma + generics.get(i);
				}
				returnTypeName += ">";
			}
			if (PrimitiveTypeUtil.isPrimitive(returnTypeName)) {
				returnDefaultValue = PrimitiveTypeUtil
						.getTypeDefaultValue(returnTypeName);
			}
		}
		// instance method
		// ex. TestTarget target = new TestTarget();
		if (!testMethod.isStatic) {
			if (testClassinfo.constructors.size() > 0) {
				ConstructorInfo constructor = testClassinfo.constructors.get(0);
				List<String> args = new ArrayList<String>();
				for (int i = 0; i < constructor.argTypes.size(); i++) {
					ArgType argType = constructor.argTypes.get(i);
					if (argType.name != null && argType.name.length() > 0) {
						sb.append(argType.name);
						sb.append(" ");
						sb.append(constructor.argNames.get(i));
						sb.append(" = ");
						if (PrimitiveTypeUtil.isPrimitive(argType.name)) {
							String primitiveDefault = PrimitiveTypeUtil
									.getTypeDefaultValue(argType.name);
							sb.append(primitiveDefault);
						} else {
							sb.append("null");
						}
						sb.append(";");
						sb.append(CRLF);
						sb.append(StrConst.tab);
						sb.append(StrConst.tab);
						args.add(constructor.argNames.get(i));
					}
				}
				sb.append(testTargetClassname);
				sb.append(" target = new ");
				sb.append(testTargetClassname);
				sb.append("(");
				if (args.size() > 0) {
					sb.append(args.get(0));
					for (int i = 1; i < args.size(); i++) {
						sb.append(",");
						sb.append(args.get(i));
					}
				}
				sb.append(");");
			} else {
				sb.append(testTargetClassname);
				sb.append(" target = new ");
				sb.append(testTargetClassname);
				sb.append("();");
			}
			sb.append(CRLF);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
		}

		// Mockito BDD
		if (pref.isTestMethodGenEnabledSupportMockito) {
			sb.append("// given");
			sb.append(CRLF);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
		}

		// args define
		// ex. String arg0 = null;
		// int arg1 = 0;
		List<ArgType> argTypes = testMethod.argTypes;
		List<String> argNames = testMethod.argNames;
		List<String> args = new ArrayList<String>();
		int argTypesLen = argTypes.size();
		if (argTypesLen > 0 && argTypes.get(0).name != null
				&& !argTypes.get(0).name.equals(StrConst.empty)) {
			for (int i = 0; i < argTypesLen; i++) {
				ArgType argType = argTypes.get(i);
				// flexible length args
				if (argType.name.matches(".+\\.\\.\\."))
					argType.name = argType.name.replaceAll("\\.\\.\\.", "[]");
				String argTypeName = getClassInSourceCode(argType.name,
						testTargetClassname, testClassinfo.importList);
				// generics
				boolean isJMock2 = pref.isTestMethodGenEnabledSupportJMock2
						&& MockGenUtil.isMockableClassName(argTypeName,
								testClassinfo.importList);
				boolean isEasyMock = pref.isTestMethodGenEnabledSupportEasyMock
						&& MockGenUtil.isMockableClassName(argTypeName,
								testClassinfo.importList);
				boolean isMockito = pref.isTestMethodGenEnabledSupportMockito
						&& MockGenUtil.isMockableClassName(argTypeName,
								testClassinfo.importList);
				boolean isJMockit = pref.isTestMethodGenEnabledSupportJMockit
						&& MockGenUtil.isMockableClassName(argTypeName,
								testClassinfo.importList);
				if (isJMock2) {
					sb.append("final ");
				}
				sb.append(argTypeName);
				// add generics
				if (argType.generics.size() > 0) {
					sb.append("<");
					sb.append(argType.generics.get(0));
					if (argType.generics.size() > 1) {
						for (int j = 1; j < argType.generics.size(); j++) {
							sb.append(StrConst.comma);
							sb.append(argType.generics.get(j));
						}
					}
					sb.append(">");
				}
				String argName = argNames.get(i);
				if (argName == null || argName.length() == 0) {
					argName = "arg" + i;
				}
				sb.append(" ");
				sb.append(argName);
				sb.append(" = ");
				if (PrimitiveTypeUtil.isPrimitive(argType.name)) {
					String primitiveDefault = PrimitiveTypeUtil
							.getTypeDefaultValue(argType.name);
					sb.append(primitiveDefault);
				} else {
					if (isJMock2) {
						sb.append(pref.isUsingJUnitHelperRuntime ? "jmock2"
								: "context");
						sb.append(".mock(");
						sb.append(argTypeName);
						sb.append(".class)");
					} else if (isEasyMock) {
						sb.append("mocks.createMock(");
						sb.append(argTypeName);
						sb.append(".class)");
					} else if (isMockito) {
						sb.append("mock(");
						sb.append(argTypeName);
						sb.append(".class)");
					} else if (isJMockit) {
						sb.append("this.");
						sb.append(testMethod.testMethodName);
						sb.append("_");
						sb.append(argName);
					} else {
						sb.append("null");
					}
				}
				sb.append(";");
				sb.append(CRLF);
				sb.append(StrConst.tab);
				sb.append(StrConst.tab);
				args.add(argName);
			}
		}
		// JMock2 expectations
		if (pref.isTestMethodGenEnabledSupportJMock2) {
			sb.append(pref.isUsingJUnitHelperRuntime ? "jmock2" : "context");
			sb.append(".checking(new Expectations(){{");
			sb.append(CRLF);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
			sb.append("// ");
			sb.append(props.get(Message.exempliGratia));
			sb.append(" : ");
			sb.append("allowing(mocked).called(); will(returnValue(1));");
			sb.append(CRLF);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
			sb.append("}});");
			sb.append(CRLF);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
		}
		// EasyMock expectations
		if (pref.isTestMethodGenEnabledSupportEasyMock) {
			sb.append("// ");
			sb.append(props.get(Message.exempliGratia));
			sb.append(" : ");
			sb.append("EasyMock.expect(mocked.called()).andReturn(1);");
			sb.append(CRLF);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
			sb.append("mocks.replay();");
			sb.append(CRLF);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
		}
		// Mockito stubbing
		// (ex.) when(hoge.doSomething()).thenReturn("abc");
		if (pref.isTestMethodGenEnabledSupportMockito) {
			sb.append("// ");
			sb.append(props.get(Message.exempliGratia));
			sb.append(" : ");
			sb.append("given(mocked.called()).willReturn(1);");
			sb.append(CRLF);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
			sb.append("// when");
			sb.append(CRLF);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
		}
		// JMockit stubbing
		// (ex.) mocked.get(anyString); returns(200);
		if (pref.isTestMethodGenEnabledSupportJMockit) {
			sb.append("new Expectations(){{");
			sb.append(CRLF);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
			sb.append("// ");
			sb.append(props.get(Message.exempliGratia));
			sb.append(" : ");
			sb.append("mocked.get(anyString); returns(200);");
			sb.append(CRLF);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
			sb.append("}};");
			sb.append(CRLF);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
		}
		if (pref.isTestMethodGenExceptions
				&& testMethod.testingTargetException != null) {
			sb.append("try{");
			sb.append(CRLF);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
		}

		// execute target method
		// ex. SampleUtil.doSomething(null, null);
		// ex. String expected = null;
		// String actual = target.doSomething();
		if (!returnTypeName.equals("void")) {
			sb.append(returnTypeName);
			sb.append(" actual = ");
		}
		if (testMethod.isStatic) {
			sb.append(testTargetClassname);
		} else {
			sb.append("target");
		}
		sb.append(StrConst.dot);
		sb.append(testMethod.methodName);
		sb.append("(");
		if (args.size() > 0 && argTypesLen > 0 && argTypes.get(0).name != null
				&& !argTypes.get(0).name.equals(StrConst.empty))
			sb.append(args.get(0));
		for (int i = 1; i < argTypes.size(); i++) {
			sb.append(", ");
			sb.append(args.get(i));
		}
		sb.append(");");
		sb.append(CRLF);

		if (pref.isTestMethodGenExceptions
				&& testMethod.testingTargetException != null) {
			// exceptions thrown patterns
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
			sb.append("fail(\"");
			sb.append(props.get(Message.expectedExceptionNotThrownMessage));
			sb.append(" (");
			sb.append(testMethod.testingTargetException.name);
			sb.append(")");
			sb.append("\");");
			sb.append(CRLF);
			sb.append(StrConst.tab);
			sb.append(StrConst.tab);
			sb.append("} catch (");
			sb.append(testMethod.testingTargetException.name);
			sb.append(" e) {}");
			sb.append(CRLF);
		} else {
			// normal patterns
			if (pref.isTestMethodGenEnabledSupportEasyMock) {
				sb.append("\t\tmocks.verify();");
				sb.append(CRLF);
			}
			if (pref.isTestMethodGenEnabledSupportMockito) {
				sb.append(StrConst.tab);
				sb.append(StrConst.tab);
				sb.append("// then");
				sb.append(CRLF);
				sb.append(StrConst.tab);
				sb.append(StrConst.tab);
				sb.append("// ");
				sb.append(props.get(Message.exempliGratia));
				sb.append(" : ");
				sb.append("verify(mocked).called();");
				sb.append(CRLF);
			}
			if (!returnTypeName.equals("void")) {
				sb.append(StrConst.tab);
				sb.append(StrConst.tab);
				sb.append(returnTypeName);
				sb.append(" expected = ");
				sb.append(returnDefaultValue);
				sb.append(";");
				sb.append(CRLF);
			}
			// assert return value
			// ex. assertEquals(expected, actual);
			if (!returnTypeName.equals("void")) {
				sb.append(StrConst.tab);
				sb.append(StrConst.tab);
				sb.append("assertEquals(expected, actual);");
				sb.append(CRLF);
			}
		}
		return sb.toString();
	}

	/**
	 * Get type.
	 * 
	 * @param arg
	 * @return
	 */
	protected static String getType(String arg) {
		arg = arg.trim().replaceAll("final ", StrConst.empty).split("\\s+")[0];
		return arg;
	}

	/**
	 * Get the available type name in method name.
	 * 
	 * @param arg
	 * @return
	 */
	protected static String getTypeAvailableInMethodName(String arg) {
		arg = arg.replaceAll(RegExp.generics, StrConst.empty);
		arg = arg.replaceAll("final ", StrConst.empty);
		arg = arg.replaceAll("\\.\\.\\.", "Array")
				.replaceAll("\\[\\]", "Array");
		// sample name classes imported or full package class defined
		// ex. java.util.Date, java.sql.Date
		arg = arg.replaceAll("\\.", StrConst.empty);
		arg = arg.trim().split(RegExp.wsPlusMax)[0];
		return arg;
	}

	/**
	 * Get the available class name in source code.
	 * 
	 * @param returnTypeToCheck
	 *            return type to check
	 * @param testTargetClassname
	 *            test target class name
	 * @param importList
	 *            import list
	 * @return result
	 */
	protected static String getClassInSourceCode(String returnTypeToCheck,
			String testTargetClassname, List<String> importList) {
		// defined class with full package
		if (returnTypeToCheck.matches(".+?\\..+"))
			return returnTypeToCheck;
		// array object
		boolean isArray = false;
		if (returnTypeToCheck.matches(".+?\\[\\]")) {
			isArray = true;
			returnTypeToCheck = returnTypeToCheck.replaceAll("\\[\\]", "");
		}
		String returnTypeName = "Object";
		boolean returnTypeFound = false;
		try {
			if (PrimitiveTypeUtil.isPrimitive(returnTypeToCheck)) {
				returnTypeFound = true;
				if (!returnTypeName.matches(".+?\\[\\]$"))
					returnTypeName = PrimitiveTypeUtil
							.getTypeDefaultValue(returnTypeToCheck);
			} else {
				try {
					Class.forName("java.lang." + returnTypeToCheck);
					returnTypeFound = true;
				} catch (Exception ignore) {
				}
				if (!returnTypeFound)
					Class.forName(returnTypeToCheck);
			}
		} catch (Exception e) {
			// class not found
			if (returnTypeToCheck.equals(testTargetClassname))
				returnTypeFound = true;
			for (String importedPackage : importList) {
				importedPackage = importedPackage.replaceAll("//",
						StrConst.empty);
				try {
					if (importedPackage.matches(".+?\\."
							+ returnTypeToCheck.replaceAll("\\[", "\\\\[")
									.replaceAll("\\]", "\\\\]") + "$")) {
						returnTypeFound = true;
						break;
					}
				} catch (Exception e2) {
					e2.printStackTrace();
				}
			}
		}
		if (!returnTypeFound) {
			return isArray ? returnTypeName + "[]" : returnTypeName;
		} else {
			return isArray ? returnTypeToCheck + "[]" : returnTypeToCheck;
		}
	}

	/**
	 * Setup required import list.
	 * 
	 * @param pref
	 *            preference loader
	 * @param alreadyImportedList
	 *            already imported list
	 * @param importList
	 *            imported list
	 * @return result
	 */
	public static List<String> setupRequiredImports(PreferenceLoader pref,
			List<String> alreadyImportedList, List<String> importList) {
		// JUnit version 3.x or 4.x
		if (pref.isJUnitVersion3) {
			// nothing to do
		} else if (pref.isJUnitVersion4) {
			if (!containsInList(alreadyImportedList, "org.junit.Test")) {
				importList.add("org.junit.Test");
			}
			if (!containsInList(alreadyImportedList,
					"static org.junit.Assert.*")) {
				importList.add("static org.junit.Assert.*");
			}
		}
		// JMock2
		if (pref.isTestMethodGenEnabledSupportJMock2) {
			if (!containsInList(alreadyImportedList, "org.jmock.Mockery")) {
				importList.add("org.jmock.Mockery");
			}
			if (!containsInList(alreadyImportedList, "org.jmock.Expectations")) {
				importList.add("org.jmock.Expectations");
			}
			if (!containsInList(alreadyImportedList,
					"org.jmock.lib.legacy.ClassImposteriser")) {
				importList.add("org.jmock.lib.legacy.ClassImposteriser");
			}
		}
		// EasyMock
		if (pref.isTestMethodGenEnabledSupportEasyMock) {
			if (!containsInList(alreadyImportedList,
					"org.easymock.classextension.EasyMock")) {
				importList.add("org.easymock.classextension.EasyMock");
			}
			if (!containsInList(alreadyImportedList,
					"org.easymock.classextension.IMocksControl")) {
				importList.add("org.easymock.classextension.IMocksControl");
			}
		}
		// Mockito
		if (pref.isTestMethodGenEnabledSupportMockito) {
			if (!containsInList(alreadyImportedList,
					"static org.mockito.BDDMockito.*")) {
				importList.add("static org.mockito.BDDMockito.*");
			}
		}
		// JMockit
		if (pref.isTestMethodGenEnabledSupportJMockit) {
			if (!containsInList(alreadyImportedList, "mockit.Mocked")) {
				importList.add("mockit.Mocked");
			}
			if (!containsInList(alreadyImportedList, "mockit.Expectations")) {
				importList.add("mockit.Expectations");
			}
		}
		return importList;
	}

	/**
	 * Check the target string value is contained in the list of string values.
	 * 
	 * @param alreadyExistList
	 *            list of string values
	 * @param checkTarget
	 *            check target string value
	 * @return result
	 */
	public static boolean containsInList(List<String> alreadyExistList,
			String checkTarget) {
		for (String alreadyExist : alreadyExistList) {
			if (checkTarget.equals(alreadyExist)) {
				return true;
			}
		}
		return false;
	}

}