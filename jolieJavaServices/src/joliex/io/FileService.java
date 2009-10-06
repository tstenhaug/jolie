/***************************************************************************
 *   Copyright (C) by Fabrizio Montesi                                     *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Library General Public License as       *
 *   published by the Free Software Foundation; either version 2 of the    *
 *   License, or (at your option) any later version.                       *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU Library General Public     *
 *   License along with this program; if not, write to the                 *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 *                                                                         *
 *   For details about the authors of this software, see the AUTHORS file. *
 ***************************************************************************/

package joliex.io;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;

import java.io.Writer;
import java.util.regex.Pattern;
import javax.activation.FileTypeMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import jolie.net.CommMessage;
import jolie.runtime.ByteArray;
import jolie.runtime.FaultException;
import jolie.runtime.JavaService;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import jolie.runtime.embedding.RequestResponse;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class FileService extends JavaService
{
	private final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
	private final TransformerFactory transformerFactory = TransformerFactory.newInstance();

	private final FileTypeMap fileTypeMap = FileTypeMap.getDefaultFileTypeMap();

	private static void readBase64IntoValue( File file, Value value )
		throws IOException
	{
		FileInputStream fis = new FileInputStream( file );
		byte[] buffer = new byte[ (int)file.length() ];
		fis.read( buffer );
		fis.close();
		sun.misc.BASE64Encoder encoder = new sun.misc.BASE64Encoder();
		value.setValue( encoder.encode( buffer ) );
	}
	
	private static void readBinaryIntoValue( File file, Value value )
		throws IOException
	{
		FileInputStream fis = new FileInputStream( file );
		byte[] buffer = new byte[ (int)file.length() ];
		fis.read( buffer );
		fis.close();
		value.setValue( new ByteArray( buffer ) );
	}

	private void readXMLIntoValue( File file, Value value )
		throws IOException
	{
		try {
			DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
			InputSource src = new InputSource( new FileReader( file ) );
			Document doc = builder.parse( src );
			value = value.getFirstChild( doc.getDocumentElement().getNodeName() );
			jolie.xml.XmlUtils.documentToValue( doc, value );
		} catch( ParserConfigurationException e ) {
			throw new IOException( e );
		} catch( SAXException e ) {
			throw new IOException( e );
		}
	}
	
	private static void readTextIntoValue( File file, Value value )
		throws IOException
	{
		String separator = System.getProperty( "line.separator" );
		StringBuffer buffer = new StringBuffer();
		String line;
		BufferedReader reader = new BufferedReader( new FileReader( file ) );
		while( (line=reader.readLine()) != null ) {
			buffer.append( line );
			buffer.append( separator );
		}
		reader.close();
		value.setValue( buffer.toString() );
	}
	
	public Value readFile( Value request )
		throws FaultException
	{
		Value filenameValue = request.getFirstChild( "filename" );

		Value retValue = Value.create();
		String format = request.getFirstChild( "format" ).strValue();
		File file = new File( filenameValue.strValue() );
		try {
			if ( "base64".equals( format ) ) {
				readBase64IntoValue( file, retValue );
			} else if ( "binary".equals( format ) ) {
				readBinaryIntoValue( file, retValue );
			} else if ( "xml".equals( format ) ) {
				readXMLIntoValue( file, retValue );
			} else {
				readTextIntoValue( file, retValue );
			}			
		} catch( FileNotFoundException e ) {
			throw new FaultException( "FileNotFound" );
		} catch( IOException e ) {
			throw new FaultException( e );
		}
		return retValue;
	}

	public String getMimeType( String filename )
		throws FaultException
	{
		File file = new File( filename );
		if ( file.exists() == false ) {
			throw new FaultException( "FileNotFound" );
		}
		return fileTypeMap.getContentType( file );
	}
	
	public String getServiceDirectory()
	{
		String dir = interpreter().programFile().getParent();
		if ( dir == null || dir.isEmpty() ) {
			dir = ".";
		}
		
		return dir;
	}
	
	public String getFileSeparator()
	{
		return jolie.lang.Constants.fileSeparator;
	}

	private void writeXML( File file, Value value )
		throws IOException
	{
		if ( value.children().isEmpty() ) {
			return; // TODO: perhaps we should erase the content of the file before returning.
		}
		try {
			Document doc = documentBuilderFactory.newDocumentBuilder().newDocument();
			String rootName = value.children().keySet().iterator().next();
			jolie.xml.XmlUtils.valueToDocument(
				value.getFirstChild( rootName ),
				rootName,
				doc
			);
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty( OutputKeys.INDENT, "yes" );
			Writer writer = new FileWriter( file );
			StreamResult result = new StreamResult( writer );
			transformer.transform( new DOMSource( doc ), result );
		} catch( ParserConfigurationException e ) {
			throw new IOException( e );
		} catch( TransformerConfigurationException e ) {
			throw new IOException( e );
		} catch( TransformerException e ) {
			throw new IOException( e );
		}
	}

	private static void writeBinary( File file, Value value )
		throws IOException
	{
		FileOutputStream os = new FileOutputStream( file );
		os.write( value.byteArrayValue().getBytes() );
		os.flush();
		os.close();
	}

	private static void writeText( File file, Value value )
		throws IOException
	{
		FileWriter writer = new FileWriter( file );
		writer.write( value.strValue() );
		writer.flush();
		writer.close();
	}

	@RequestResponse
	public void writeFile( Value request )
		throws FaultException
	{
		Value filenameValue = request.getFirstChild( "filename" );
		if ( !filenameValue.isString() ) {
			throw new FaultException( "FileNotFound" );
		}
		Value content = request.getFirstChild( "content" );
		String format = request.getFirstChild( "format" ).strValue();
		File file = new File( filenameValue.strValue() );
		try {
			if ( "text".equals( format ) ) {
				writeText( file, content );
			} else if ( "binary".equals( format ) ) {
				writeBinary( file, content );
			} else if ( "xml".equals( format ) ) {
				writeXML( file, content );
			} else if ( format.isEmpty() ) {
				if ( content.isByteArray() ) {
					writeBinary( file, content );
				} else {
					writeText( file, content );
				}
			}
		} catch( IOException e ) {
			throw new FaultException( "IOException", e );
		}
	}
	
	public Boolean delete( CommMessage request )
	{
		String filename = request.value().strValue();
		boolean isRegex = request.value().getFirstChild( "isRegex" ).intValue() > 0;
		boolean ret = true;
		if ( isRegex ) {
			File dir = new File( filename ).getAbsoluteFile().getParentFile();
			String[] files = dir.list( new ListFilter( filename ) );
			if ( files != null ) {
				for( String file : files ) {
					new File( file ).delete();
				}
			}
		} else {
			if ( new File( filename ).delete() == false ) {
				ret = false;
			}
		}
		return ret;
	}

	@RequestResponse
	public void rename( Value request )
		throws FaultException
	{
		String filename = request.getFirstChild( "filename" ).strValue();
		String toFilename = request.getFirstChild( "to" ).strValue();
		if ( new File( filename ).renameTo( new File( toFilename ) ) == false ) {
			throw new FaultException( "IOException" );
		}
	}
	
	public Value list( Value request )
	{
		File dir = new File( request.getFirstChild( "directory" ).strValue() );
		String[] files = dir.list( new ListFilter( request.getFirstChild( "regex" ).strValue() ) );
		Value response = Value.create();
		if ( files != null ) {
			ValueVector results = response.getChildren( "result" );
			for( String file : files ) {
				results.add( Value.create( file ) );
			}
		}
		return response;
	}
	
	private static class ListFilter implements FilenameFilter
	{
		final private Pattern pattern;
		public ListFilter( String regex )
		{
			this.pattern = Pattern.compile( regex );
		}

		public boolean accept( File file, String name )
		{
			return pattern.matcher( name ).matches();
		}
	}
}
