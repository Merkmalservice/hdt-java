package org.rdfhdt.hdt.dictionary;
/*
 * File: $HeadURL: https://hdt-java.googlecode.com/svn/trunk/hdt-java/iface/org/rdfhdt/hdt/dictionary/DictionarySection.java $
 * Revision: $Rev: 191 $
 * Last modified: $Date: 2013-03-03 11:41:43 +0000 (dom, 03 mar 2013) $
 * Last modified by: $Author: mario.arias $
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Contacting the authors:
 *   Mario Arias:               mario.arias@deri.org
 *   Javier D. Fernandez:       jfergar@infor.uva.es
 *   Miguel A. Martinez-Prieto: migumar2@infor.uva.es
 *   Alejandro Andres:          fuzzy.alej@gmail.com
 */


import java.io.Closeable;
import java.util.Iterator;

/**
 * Interface that specifies the basic methods for a dictionary section
 *
 * @author mario.arias, Eugen
 *
 */
public interface DictionarySection extends Closeable {

	/**
	 * Find a String and return its ID.
	 * @param s
	 */
	public long locate(CharSequence s);

	/**
	 * Find the String associated to a given ID
	 * @param pos
	 */
	public CharSequence extract(long pos);

	/**
	 * Size in bytes of the strings held in the dictionary section.
	 */
	long size();

	/**
	 * Number of entries in the dictionary section.
	 */
	public long getNumberOfElements();

	/**
	 * Iterator over all entries in the dictionary, sorted lexicographically.
	 */
	Iterator<? extends CharSequence> getSortedEntries();

}
