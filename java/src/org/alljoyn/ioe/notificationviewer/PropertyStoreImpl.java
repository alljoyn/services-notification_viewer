/******************************************************************************
 * Copyright (c) 2013-2014, AllSeen Alliance. All rights reserved.
 *
 *    Permission to use, copy, modify, and/or distribute this software for any
 *    purpose with or without fee is hereby granted, provided that the above
 *    copyright notice and this permission notice appear in all copies.
 *
 *    THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 *    WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 *    MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 *    ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 *    WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 *    ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 *    OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 ******************************************************************************/

package org.alljoyn.ioe.notificationviewer;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alljoyn.about.AboutKeys;
import org.alljoyn.services.common.PropertyStore;
import org.alljoyn.services.common.PropertyStoreException;

import android.content.SharedPreferences;

public class  PropertyStoreImpl implements PropertyStore{
	
	  public static class Property
    {	    		
    	private  String m_language=null;
    	private final boolean m_isWritable;
    	private final boolean m_isAnnounced;
    	private final boolean m_isPublic;
    	private final String m_name;
    	private Object m_object=null;
      // public.write.announce
    	
    	
    	public Property(String m_name,Object value, boolean isPublic,boolean isWritable,boolean isAnnounced)
    	{
    		super();
    		this.m_isWritable = isWritable;
    		this.m_isAnnounced = isAnnounced;
    		this.m_isPublic = isPublic;
    		this.m_name = m_name;
    		this.m_object=value;
    	}
    	
    	public boolean isWritable()
    	{
    		return m_isWritable;
    	}
    	public boolean isAnnounced()
    	{
    		return m_isAnnounced;
    	}
    	public boolean isPublic()
    	{
    		return m_isPublic;
    	}
    	public String getName()
    	{
    		return m_name;
    	}
    	
    	public String getLangauge()
    	{
    		return m_language;
    	}	    	
    	public void setLanguage(String language ) { this.m_language = language; }	 
    	
    	public Object getObject() {
    		return m_object;
    	}
    }

	  
	private Map<String,List<Property>> m_internalMap=null;
	private SharedPreferences sharedPrefs;
	
	public PropertyStoreImpl(Map<String,List<Property>>   dataMap, SharedPreferences sharedPrefs){
		m_internalMap=dataMap;
		this.sharedPrefs = sharedPrefs;
	}
	
	@Override
    public void readAll(String languageTag, Filter filter, Map<String, Object> dataMap) throws PropertyStoreException{
		if (filter==Filter.ANNOUNCE)
		{	    		
			
			if (m_internalMap!=null)
			{		    			
				  List<Property> langauge=m_internalMap.get(AboutKeys.ABOUT_DEFAULT_LANGUAGE);
				  if (langauge!=null)
				  {
					  languageTag=(String)langauge.get(0).getObject();
				  }else{
					  throw new PropertyStoreException(PropertyStoreException.UNSUPPORTED_LANGUAGE);
				  }
				  	    				
				  Set<Map.Entry<String, List<Property>>> entries = m_internalMap.entrySet();	    				
				  for(Map.Entry<String, List<Property>> entry : entries) {
					  	String key = entry.getKey();
					  	List<Property> properyList = entry.getValue();
					  	for (int i=0;i<properyList.size();i++)
					  	{
					  		Property property=properyList.get(i);
					  		if (!property.isAnnounced())
					  			continue;
					  		 if (!(property.getLangauge()==null|| property.getLangauge().compareTo(languageTag) == 0))
				                continue;
					  		dataMap.put(key, property.getObject());	    					  		
					  	}	    					  	
			      }	    					    				
			}else 
				throw new  PropertyStoreException(PropertyStoreException.UNSUPPORTED_KEY); 
			
			
		}
		else if (filter==Filter.READ)
		{	    			
			if (languageTag!=null && languageTag.length()>1)
			{
  			 List<Property> supportedLanguages=m_internalMap.get(AboutKeys.ABOUT_SUPPORTED_LANGUAGES);		    			
  			 if (supportedLanguages==null)
  					throw new  PropertyStoreException(PropertyStoreException.UNSUPPORTED_KEY); 
  			if (!( supportedLanguages.get(0).getObject() instanceof Set<?>)){	    						
					throw new  PropertyStoreException(PropertyStoreException.UNSUPPORTED_LANGUAGE);
				}else{
					@SuppressWarnings("unchecked")
					Set<String> languages=(Set<String>)supportedLanguages.get(0).getObject();
					if (!languages.contains(languageTag)){
						throw new  PropertyStoreException(PropertyStoreException.UNSUPPORTED_LANGUAGE);
					}								
				}
			}else{
				
				 List<Property> langauge=m_internalMap.get(AboutKeys.ABOUT_DEFAULT_LANGUAGE);
				  if (langauge!=null)
				  {
					  languageTag=(String)langauge.get(0).getObject();
				  }else{
					  throw new PropertyStoreException(PropertyStoreException.UNSUPPORTED_LANGUAGE);
				  }
			}	    			 	    			
		  Set<Map.Entry<String, List<Property>>> entries = m_internalMap.entrySet();    				
		  for(Map.Entry<String, List<Property>> entry : entries) {
			  	String key = entry.getKey();
			  	List<Property> properyList = entry.getValue();
			  	for (int i=0;i<properyList.size();i++)
			  	{
			  		Property property=properyList.get(i);
			  		if (!property.isPublic())
			  			continue;
			  		 if (!(property.getLangauge()==null|| property.getLangauge().compareTo(languageTag) == 0))
		                continue;
			  		dataMap.put(key, property.getObject());	    					  		
			  	}	    					  	
	      }
			
		}//end of read.
        else if (filter == Filter.WRITE) {
            Set<Map.Entry<String, List<Property>>> entries = m_internalMap.entrySet();                      
            for(Map.Entry<String, List<Property>> entry : entries) {
                  String key = entry.getKey();
                  List<Property> properyList = entry.getValue();
                  for (int i=0;i<properyList.size();i++)
                  {
                      Property property=properyList.get(i);
//                      if (!(property.getLangauge()==null|| property.getLangauge().compareTo(languageTag) == 0))
//                          continue;
                      if (property.isWritable()) {
                          dataMap.put(key, property.getObject());                                 
                      }
                  }                               
            }     
        }//end of write
		else throw new PropertyStoreException(PropertyStoreException.ILLEGAL_ACCESS);
		
		
	}
	
	@Override
    public void update(String key, String languageTag, Object newValue) throws PropertyStoreException{
//	      languageTag = checkLanguage(languageTag);
	        if (!m_internalMap.containsKey(key))
	        {
	        throw new PropertyStoreException(PropertyStoreException.INVALID_VALUE);
	        }
	        
	        m_internalMap.get(key).get(0).m_object = newValue;
	        
            //TODO: Refactor this later?
	        if (null != key && key.equals(AboutKeys.ABOUT_DEVICE_NAME)) {
	            SharedPreferences.Editor editor = sharedPrefs.edit();
	            editor.putString(Constants.SHARED_PREFS_KEY_DEVICE_NAME, newValue.toString());
	            editor.apply();
	        }
	}

	@Override
    public void reset(String key, String languageTag) throws PropertyStoreException{
		
	}
	
	@Override
    public void resetAll() throws PropertyStoreException{
		
	}
	
}