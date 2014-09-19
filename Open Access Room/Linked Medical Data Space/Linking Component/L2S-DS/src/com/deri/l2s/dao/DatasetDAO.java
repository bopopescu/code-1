package com.deri.l2s.dao;

/*
 *  Linked2Safety SPARQL Endpoint Discovery and Linking Framework
 *  
 *  Copyright (C) 2014 Muntazir Mehdi <muntazir.mehdi@insight-centre.org>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

import java.util.List;

import javax.persistence.EntityManager;

import com.deri.l2s.entities.Dataset;
import com.deri.l2s.managers.EMProvider;

public class DatasetDAO {
	
	private EntityManager em;
	
	public DatasetDAO(){
		em = EMProvider.getEMProvider().getEm();
	}
	
	public List<Dataset> getAllDatasets(){
		return em.createQuery("SELECT d FROM Dataset d").getResultList();
	}
	
	public List<Dataset> getSomeDatasets(){
		return em.createQuery("SELECT d FROM Dataset d WHERE d.id > 0 and d.id < 51").getResultList();
	}
	
	public List<Dataset> getDataById(int id) {
		return em.createQuery("SELECT d FROM Dataset d WHERE d.id = " + id).getResultList();
	}
	
	public void flagDataset(Dataset dataset){
		EntityManager em = EMProvider.getEMProvider().getEm();
		dataset.setFlag(true);
		em.getTransaction().begin();
		em.merge(dataset);
		em.getTransaction().commit();
	}

}