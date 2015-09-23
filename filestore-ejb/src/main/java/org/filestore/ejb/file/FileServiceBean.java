package org.filestore.ejb.file;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Remote;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.filestore.ejb.file.entity.FileItem;
import org.filestore.ejb.store.BinaryStoreService;
import org.filestore.ejb.store.BinaryStoreServiceException;
import org.filestore.ejb.store.BinaryStreamNotFoundException;

@Stateless(name = "fileservice")
@Remote(FileService.class)
public class FileServiceBean implements FileService {
	
	private static final Logger LOGGER = Logger.getLogger(FileServiceBean.class.getName());
	
	@PersistenceContext(unitName="filestore-pu")
	protected EntityManager em;
	@Resource
	protected SessionContext ctx;
	@EJB
	protected BinaryStoreService store;

	@Override
	@TransactionAttribute(TransactionAttributeType.REQUIRED)
	public String postFile(String owner, List<String> receivers, String message, String name, InputStream stream) throws FileServiceException {
		LOGGER.log(Level.INFO, "Post File called");
		try {
			String streamid = store.put(stream);
			String id = UUID.randomUUID().toString().replaceAll("-", "");
			FileItem file = new FileItem();
			file.setId(id);
			file.setOwner(owner);
			file.setReceivers(receivers);
			file.setMessage(message);
			file.setName(name);
			file.setStream(streamid);
			em.persist(file);
			return id;
		} catch ( BinaryStoreServiceException e ) {
			LOGGER.log(Level.SEVERE, "An error occured during storing binary content", e);
			ctx.setRollbackOnly();
			throw new FileServiceException("An error occured during storing binary content", e);
		} catch ( Exception e ) {
			LOGGER.log(Level.SEVERE, "unexpected error during posting file", e);
			throw new FileServiceException(e);
		}
	}

	@Override
	@TransactionAttribute(TransactionAttributeType.SUPPORTS)
	public FileItem getFile(String id) throws FileServiceException {
		LOGGER.log(Level.INFO, "Get File called");
		try {
			FileItem item = em.find(FileItem.class, id);
			if ( item == null ) {
				throw new FileServiceException("Unable to get file with id '" + id + "' : file does not exists");
			}
			return item;
		} catch ( Exception e ) {
			LOGGER.log(Level.SEVERE, "An error occured during getting file", e);
			throw new FileServiceException(e);
		}
	}
	
	@Override
	@TransactionAttribute(TransactionAttributeType.SUPPORTS)
	public InputStream getFileContent(String id) throws FileServiceException {
		LOGGER.log(Level.INFO, "Get File Content called");
		try {
			FileItem item = em.find(FileItem.class, id);
			if ( item == null ) {
				throw new FileServiceException("Unable to get file with id '" + id + "' : file does not exists");
			}
			InputStream is = store.get(item.getStream()); 
			return is;
		} catch ( BinaryStreamNotFoundException e ) {
			LOGGER.log(Level.SEVERE, "No binary content found for this file item !!", e);
			throw new FileServiceException("No binary content found for this file item !!", e);
		} catch ( BinaryStoreServiceException e ) {
			LOGGER.log(Level.SEVERE, "An error occured during reading binary content", e);
			throw new FileServiceException("An error occured during reading binary content", e);
		} catch ( Exception e ) {
			LOGGER.log(Level.SEVERE, "unexpected error during getting file", e);
			throw new FileServiceException(e);
		}
	}

	@Override
	@TransactionAttribute(TransactionAttributeType.REQUIRED)
	public void deleteFile(String id) throws FileServiceException {
		LOGGER.log(Level.INFO, "Delete File called");
		try {
			FileItem item = em.find(FileItem.class, id);
			if ( item == null ) {
				throw new FileServiceException("Unable to delete file with id '" + id + "' : file does not exists");
			}
			em.remove(item);
			try {
				store.delete(item.getStream());
			} catch ( BinaryStreamNotFoundException | BinaryStoreServiceException e ) {
				LOGGER.log(Level.WARNING, "unable to delete binary content, may result in orphean file", e);
			}
		} catch ( Exception e ) {
			LOGGER.log(Level.SEVERE, "unexpected error during deleting file", e);
			ctx.setRollbackOnly();
			throw new FileServiceException(e);
		}	
	}

	

}