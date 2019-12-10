package org.geoserver.web.publishtool;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.FileUploadField;
import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.util.lang.Bytes;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.web.CatalogIconFactory;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.GeoServerSecuredPage;
import org.geoserver.web.data.layer.NewLayerPageProvider;
import org.geoserver.web.data.layer.Resource;
import org.geoserver.web.data.resource.ResourceConfigurationPage;
import org.geoserver.web.wicket.GeoServerTablePanel;
import org.geoserver.web.wicket.ParamResourceModel;
import org.geoserver.web.wicket.SimpleAjaxLink;
import org.geoserver.web.wicket.GeoServerDataProvider.Property;
import org.geotools.util.logging.Logging;
import org.opengis.filter.Filter;

public class TestTool extends GeoServerSecuredPage {

	static final Logger LOGGER = Logging.getLogger(TestTool.class);

	DropDownChoice<Tuple> workspace;

	DropDownChoice<Tuple> store;

	DropDownChoice<Tuple> resourceAndLayer;

	private FileUploadField fileUploadField;

	/**
	 * DropDown choice model object becuase dbconfig freaks out if using the
	 * CatalogInfo objects directly
	 */
	private static final class Tuple implements Serializable, Comparable<Tuple> {
		private static final long serialVersionUID = 1L;

		final String id, name;

		public Tuple(String id, String name) {
			this.id = id;
			this.name = name;
		}

		@Override
		public int compareTo(Tuple o) {
			return name.compareTo(o.name);
		}
	}

	private static class TupleChoiceRenderer extends ChoiceRenderer<Tuple> {
		private static final long serialVersionUID = 1L;

		@Override
		public Object getDisplayValue(Tuple object) {
			return object.name;
		}

		@Override
		public String getIdValue(Tuple object, int index) {
			return object.id;
		}
	}

	public TestTool() {
		super();
		setDefaultModel(new Model());
		Form form = new Form("form", new Model());
		add(form);

		IModel<List<Tuple>> wsModel = new LoadableDetachableModel<List<Tuple>>() {
			private static final long serialVersionUID = 1L;

			@Override
			protected List<Tuple> load() {
				Catalog catalog = GeoServerApplication.get().getCatalog();
				Filter filter = Predicates.acceptAll();
				CloseableIterator<WorkspaceInfo> list = catalog.list(WorkspaceInfo.class, filter, null, 4000, null);
				List<Tuple> workspaces;
				try {
					workspaces = Lists.newArrayList(Iterators.transform(list, new Function<WorkspaceInfo, Tuple>() {
						@Override
						public Tuple apply(WorkspaceInfo input) {
							return new Tuple(input.getId(), input.getName());
						}
					}));
				} finally {
					list.close();
				}
				Collections.sort(workspaces);
				return workspaces;
			}
		};
		workspace = new DropDownChoice<Tuple>("workspace", new Model<Tuple>(), wsModel, new TupleChoiceRenderer());
		workspace.setNullValid(true);

		workspace.setOutputMarkupId(true);
		workspace.setRequired(true);
		form.add(workspace);
		workspace.add(new OnChangeAjaxBehavior() {
			private static final long serialVersionUID = -5613056077847641106L;

			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				target.add(store);
				target.add(resourceAndLayer);
			}
		});

		IModel<List<Tuple>> storesModel = new LoadableDetachableModel<List<Tuple>>() {
			private static final long serialVersionUID = 1L;

			@Override
			protected List<Tuple> load() {
				Catalog catalog = GeoServerApplication.get().getCatalog();
				Tuple ws = workspace.getModelObject();
				if (ws == null) {
					return Lists.newArrayList();
				}
				Filter filter = Predicates.equal("workspace.id", ws.id);
				int limit = 100;
				CloseableIterator<StoreInfo> iter = catalog.list(StoreInfo.class, filter, null, limit, null);

				List<Tuple> stores;
				try {
					stores = Lists.newArrayList(Iterators.transform(iter, new Function<StoreInfo, Tuple>() {

						@Override
						public Tuple apply(StoreInfo input) {
							return new Tuple(input.getId(), input.getName());
						}
					}));
				} finally {
					iter.close();
				}
				Collections.sort(stores);
				return stores;
			}
		};

		store = new DropDownChoice<Tuple>("store", new Model<Tuple>(), storesModel, new TupleChoiceRenderer());
		store.setNullValid(true);

		store.setOutputMarkupId(true);
		store.add(new OnChangeAjaxBehavior() {
			private static final long serialVersionUID = -5333344688588590014L;

			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				target.add(resourceAndLayer);
			}
		});
		form.add(store);

		IModel<List<Tuple>> resourcesModel = new LoadableDetachableModel<List<Tuple>>() {
			private static final long serialVersionUID = 1L;

			@Override
			protected List<Tuple> load() {
				Catalog catalog = getCatalog();
				Tuple storeInfo = store.getModelObject();
				if (storeInfo == null) {
					return Lists.newArrayList();
				}
				Integer limit = 100;
				Filter filter = Predicates.equal("store.id", storeInfo.id);
				CloseableIterator<ResourceInfo> iter = catalog.list(ResourceInfo.class, filter, null, limit, null);

				List<Tuple> resources;
				try {
					resources = Lists.newArrayList(Iterators.transform(iter, new Function<ResourceInfo, Tuple>() {
						@Override
						public Tuple apply(ResourceInfo input) {
							return new Tuple(input.getId(), input.getName());
						}
					}));
				} finally {
					iter.close();
				}
				Collections.sort(resources);
				return resources;
			}
		};

		resourceAndLayer = new DropDownChoice<Tuple>("resourceAndLayer", new Model<Tuple>(), resourcesModel,
				new TupleChoiceRenderer());
		resourceAndLayer.setNullValid(true);

		resourceAndLayer.setOutputMarkupId(true);
		form.add(resourceAndLayer);

//      todo  参考NewLayerPage读取数据
		String storeId = store.getId();
		// the layer choosing block
		// visible when in any way a store has been chosen
		WebMarkupContainer selectLayersContainer = new WebMarkupContainer("selectLayersContainer");
		selectLayersContainer.setOutputMarkupId(true);
		add(selectLayersContainer);
		WebMarkupContainer selectLayers = new WebMarkupContainer("selectLayers");
		selectLayers.setVisible(true);
		selectLayersContainer.add(selectLayers);

		Label storeName;
		selectLayers.add(storeName = new Label("storeName", new Model<String>()));
//        if (storeId != null) {
//            StoreInfo store = getCatalog().getStore(storeId, StoreInfo.class);
//            storeName.setDefaultModelObject(store.getName());
//        }

		NewLayerPageProvider provider = new NewLayerPageProvider();
		provider.setStoreId(storeId);
		provider.setShowPublished(true);
		GeoServerTablePanel<Resource> layers = new GeoServerTablePanel<Resource>("layers", provider) {

			@Override
			protected Component getComponentForProperty(String id, IModel<Resource> itemModel,
					Property<Resource> property) {
				if (property == NewLayerPageProvider.NAME) {
					return new Label(id, property.getModel(itemModel));
				} else if (property == NewLayerPageProvider.PUBLISHED) {
					final Resource resource = itemModel.getObject();
					final CatalogIconFactory icons = CatalogIconFactory.get();
					if (resource.isPublished()) {
						PackageResourceReference icon = icons.getEnabledIcon();
						Fragment f = new Fragment(id, "iconFragment", selectLayers);
						f.add(new Image("layerIcon", icon));
						return f;
					} else {
						return new Label(id);
					}
				} else if (property == NewLayerPageProvider.ACTION) {
					final Resource resource = itemModel.getObject();
					if (resource.isPublished()) {
						return resourceChooserLink(id, itemModel, new ParamResourceModel("publishAgain", this));
					} else {
						return resourceChooserLink(id, itemModel, new ParamResourceModel("publish", this));
					}
				} else {
					throw new IllegalArgumentException("Don't know of property " + property.getName());
				}
			}
		};
		layers.setFilterVisible(true);

		selectLayers.add(layers);

		fileUploadField = new FileUploadField("fileUploadField");
		Form uploadForm = new Form("uploadform") {
			@Override
			protected void onSubmit() {
				super.onSubmit();

				FileUpload fileUpload = fileUploadField.getFileUpload();

				try {
					File file = new File(System.getProperty("user.dir") + "/" + fileUpload.getClientFileName());
					fileUpload.writeTo(file);
					Convert2Shp converter = new Convert2Shp(getCatalog());
				} catch (IOException e) {
					e.printStackTrace();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};

		uploadForm.setMultiPart(true);
		// set a limit for uploaded file's size
		uploadForm.setMaxSize(Bytes.kilobytes(100));
		uploadForm.add(fileUploadField);
		add(new FeedbackPanel("feedbackPanel"));
		add(uploadForm);
	}
	
    SimpleAjaxLink<Resource> resourceChooserLink(
            String id, IModel<Resource> itemModel, IModel<String> label) {
        return new SimpleAjaxLink<Resource>(id, itemModel, label) {

            @Override
            protected void onClick(AjaxRequestTarget target) {
                Resource resource = (Resource) getDefaultModelObject();
//                setResponsePage(new ResourceConfigurationPage(buildLayerInfo(resource), true));
            }
        };
    }
}
