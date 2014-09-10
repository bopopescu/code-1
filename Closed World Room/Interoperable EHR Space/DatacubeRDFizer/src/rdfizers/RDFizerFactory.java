/*
 *   Copyright (C) 2014 Project dntalaperas@ubitech.eu

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
   
   This component links (bundles) to Apache Jena, which is available under Apache Licence 2.
   You may download the source code and associated license at https://jena.apache.org/
    
   The source files are available at:  https://github.com/linked2safety/code 
*/


package rdfizers;

public class RDFizerFactory
{
    public static RDFizerInterface getRDFizer(String rdfizerName, String path, String mappingPath, String outpath)
    throws ClassNotFoundException, InstantiationException, IllegalAccessException
    {
        Class rdfizerClass = null;
        RDFizerInterface rdfizer = null;
        
        if(rdfizerName.equals("csv"))
            rdfizerClass = Class.forName("rdfizers.CSVRDFizer");
        
        rdfizer = (RDFizerInterface)rdfizerClass.newInstance();
        
        rdfizer.setCsvPath(path);
        rdfizer.setMappingPath(mappingPath);
        rdfizer.setOutputPath(outpath);

        return rdfizer;
    }
}
