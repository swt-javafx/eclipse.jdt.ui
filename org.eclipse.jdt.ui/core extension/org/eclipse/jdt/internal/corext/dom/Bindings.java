/*
 * (c) Copyright IBM Corp. 2000, 2001, 2002.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.dom;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.Type;

import org.eclipse.jdt.internal.corext.Assert;

public class Bindings {
	
	private Bindings() {
		// No instance
	}
	
	/**
	 * Checks if the two bindings are equals. First an identity check is
	 * made an then the key of the bindings are compared. 	 * @param b1 first binding treated as <code>this</code>. So it must
	 *  not be <code>null</code>	 * @param b2 the second binding.	 * @return boolean	 */
	public static boolean equals(IBinding b1, IBinding b2) {
		Assert.isTrue(b1 != null);
		if (b1 == b2)
			return true;
		if (b2 == null)
				return false;		
		String k1= b1.getKey();
		String k2= b2.getKey();
		if (k1 == null || k2 == null)
				return false;
		if (k1 != null)
			return k1.equals(k2);
		else
			return k2.equals(k1);
	}
	
	private static boolean isPrimitiveOrArrayOfPrimitive(ITypeBinding type) {
		return (type.isPrimitive() || (type.isArray() && type.getElementType().isPrimitive()));
	}
	
	public static String asString(IMethodBinding method) {
		StringBuffer result= new StringBuffer(method.getName());
		result.append('(');
		ITypeBinding[] parameters= method.getParameterTypes();
		int lastComma= parameters.length - 1;
		for (int i= 0; i < parameters.length; i++) {
			ITypeBinding parameter= parameters[i];
			result.append(parameter.getName());
			if (i < lastComma)
				result.append(", "); //$NON-NLS-1$
		}
		result.append(')');
		return result.toString();
	}
	
	public static String getTypeQualifiedName(ITypeBinding type) {
		if (isPrimitiveOrArrayOfPrimitive(type))
			return type.getName();
		StringBuffer buffer= new StringBuffer();
		createName(buffer, type);
		return buffer.toString();
	}
	
	public static String getFullyQualifiedName(ITypeBinding type) {
		if (isPrimitiveOrArrayOfPrimitive(type))
			return type.getName();
		StringBuffer buffer= new StringBuffer();
		if (!type.getPackage().isUnnamed()) {
			buffer.append(type.getPackage().getName());
			buffer.append('.');
		}
		createName(buffer, type);
		return buffer.toString();		
	}
	
	public static String getFullyQualifiedImportName(ITypeBinding type) {
		if (type.isArray())
			return getFullyQualifiedName(type.getElementType());
		else if (type.isAnonymous())
			return getFullyQualifiedImportName(type.getSuperclass());
		else
			return getFullyQualifiedName(type);
	}
	
	public static String[] getNameComponents(ITypeBinding type) {
		if (isPrimitiveOrArrayOfPrimitive(type))
			return new String[] { type.getName() };
		List result= new ArrayList(3);
		while(type != null) {
			if (type.isAnonymous())
				result.add(0, "$local$"); //$NON-NLS-1$
			else
				result.add(0, type.getName());
			type= type.getDeclaringClass();
		}
		return (String[]) result.toArray(new String[result.size()]);
	}
	
	public static String[] getAllNameComponents(ITypeBinding type) {
		String[] typeComponents= getNameComponents(type);
		if (isPrimitiveOrArrayOfPrimitive(type))
			return typeComponents;
		IPackageBinding pack= type.getPackage();
		if (pack.isUnnamed())
			return typeComponents;
		String[] packComponents= pack.getNameComponents();
		String[] result= new String[packComponents.length + typeComponents.length];
		System.arraycopy(packComponents, 0, result, 0, packComponents.length);
		System.arraycopy(typeComponents, 0, result, packComponents.length, typeComponents.length);
		return result;
	}
	
	public static ITypeBinding getTopLevelType(ITypeBinding type) {
		ITypeBinding parent= type.getDeclaringClass();
		while (parent != null) {
			type= parent;
			parent= type.getDeclaringClass();
		}
		return type;
	}
	
	public static Type createType(ITypeBinding binding, AST ast) {
		if (binding.isPrimitive()) {
			String name= binding.getName();
			return ast.newPrimitiveType(PrimitiveType.toCode(name));
		} else if (binding.isArray()) {
			Type elementType= createType(binding.getElementType(), ast);
			return ast.newArrayType(elementType, binding.getDimensions());
		} else {
			return ast.newSimpleType(ast.newName(Bindings.getAllNameComponents(binding)));
		}
	}

	/**
	 * Checks whether	the passed type binding is a runtime exception.
	 * 
	 * @return <code>true</code> if the passed type binding is a runtime exception;
	 * 	otherwise <code>false</code> is returned
	 */
	public static boolean isRuntimeException(ITypeBinding thrownException, AST ast) {
		if (thrownException == null || thrownException.isPrimitive() || thrownException.isArray())
			return false;
		
		ITypeBinding runTimeException= ast.resolveWellKnownType("java.lang.RuntimeException"); //$NON-NLS-1$
		while (thrownException != null) {
			if (runTimeException == thrownException)
				return true;
			thrownException= thrownException.getSuperclass();
		}
		return false;
	}
	
	/**
	 * Finds the method specified by <code>methodName<code> and </code>parameters</code> in
	 * the given <code>type</code>. Returns <code>null</code> if no such method exits.
	 */
	public static IMethodBinding findMethodInType(ITypeBinding type, String methodName, ITypeBinding[] parameters) {
		if (type.isPrimitive())
			return null;
		IMethodBinding[] methods= type.getDeclaredMethods();
		for (int i= 0; i < methods.length; i++) {
			if (isEqualMethod(methods[i], methodName, parameters))
				return methods[i];
		}
		return null;
	}
	
	/**
	 * Finds the method specified by <code>methodName<code> and </code>parameters</code> in
	 * the type hierarchy denoted by the given type. Returns <code>null</code> if no such method
	 * exists.
	 */
	public static IMethodBinding findMethodInHierarchy(ITypeBinding type, String methodName, ITypeBinding parameters[]) {
		while (type != null) {
			IMethodBinding method= findMethodInType(type, methodName, parameters);
			if (method != null)
				return method;
			type= type.getSuperclass();
		}
		return null;
	}
	
	public static boolean isEqualMethod(IMethodBinding method, String methodName, ITypeBinding[] parameters) {
		if (!method.getName().equals(methodName))
			return false;
			
		ITypeBinding[] methodParameters= method.getParameterTypes();
		if (methodParameters.length != parameters.length)
			return false;
		for (int i= 0; i < parameters.length; i++) {
			if (parameters[i] != methodParameters[i])
				return false;
		}
		return true;
	}	
	
	private static void createName(StringBuffer buffer, ITypeBinding type) {
		ITypeBinding declaringType= type.getDeclaringClass();
		if (declaringType != null) {
			createName(buffer, declaringType);
			buffer.append('.');
		}
		buffer.append(type.getName());
	}
}
