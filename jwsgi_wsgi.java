import java.util.*;
import java.io.*;
import org.python.core.*;
import org.python.util.InteractiveConsole;

public class jwsgi_wsgi {

	// the wsgi callable
	PyObject app;

	// this proxy class ensures bytearray is always returned
	public class WSGIresponseProxy implements Iterator {
		Iterator o;
		public WSGIresponseProxy(PyObject po) {
			o = po.asIterable().iterator();
		}
		public boolean hasNext() {
			return o.hasNext();
		}

		public void remove() {
			o.remove();
		}

		public Object next() {
			PyString ps = (PyString) o.next();
			return ps.toBytes();
		}

		public Iterator iterator() {
			return this;
		}
	}

	public class WSGIresponse extends PyObject {
		HashMap<String,Object> headers;
		int status;
		PyObject app;
		PyObject env;
		
		public WSGIresponse(PyObject application, PyObject environ) {
			headers = new HashMap<String,Object>();
			app = application;
			env = environ;
		}

		// this is our start_response implementation
		public PyObject __call__(PyObject status_str, PyObject hh) {
			if (status_str.__len__() >= 3) {
				status = Integer.parseInt(status_str.toString().substring(0,3));
			}
			PyObject iter = hh.__iter__();
                	for (PyObject item; (item = iter.__iternext__()) != null;) {
				PyTuple ht = (PyTuple) item;
				headers.put(ht.get(0).toString(), ht.get(1).toString());
			}
			return null;
		}

		public PyObject __call__(PyObject status_str, PyObject hh, PyObject exc) {
			return __call__(status_str, hh);
		}

		public Object[] toJWSGI() {
			WSGIresponseProxy body = new WSGIresponseProxy(app.__call__(env, this));
			Object[] response = { status, headers, body };
			return response;
		}
		
	}

	public class WSGIinput extends PyObject {
                public PyString read() {
                        return null;
                }

                public PyString read(int len) {
                        return null;
                }

                public PyString readline() {
                        return null;
                }

                public PyString readline(int hint) {
                        return null;
                }

                public PyList readlines() {
                        return null;
                }

                public PyList readlines(int hint) {
                        return null;
                }
        }
	
	public jwsgi_wsgi() throws Exception {
		// check for the virtual option
		if (!uwsgi.opt.containsKey("jwsgi-wsgi")) {
			throw new Exception("you have to specify the module of a wsgi application with the \"jwsgi-wsgi\" virtual option");	
		}
		System.out.println("launching jython...");
		imp.load("site");
		// fix sys.api_version
		Py.getSystemState().__dict__.__setitem__("api_version", new PyInteger(1013));
		System.out.println(InteractiveConsole.getDefaultBanner());
		PyModule app_module = (PyModule) imp.importName((String)uwsgi.opt.get("jwsgi-wsgi"), false);
		PyStringMap app_dict = (PyStringMap) app_module.__dict__;
		app = app_dict.get(new PyString("application"));
	}

	public Object[] application(HashMap env) throws Exception {

		// fill the environ
		PyDictionary wsgi_env = new PyDictionary();
		Iterator it = env.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry entry = (Map.Entry)it.next();
			Object o_key = entry.getKey();
			Object o_value = entry.getValue();
			if (o_key instanceof String && o_value instanceof String) {
				String str_key = (String) o_key;
				String str_value = (String) o_value;
				wsgi_env.__setitem__(str_key, new PyString(str_value));
			}
		}

		wsgi_env.__setitem__("wsgi.input", new WSGIinput());

		// call the WSGI callable and translate to JWSGI
		WSGIresponse r = new WSGIresponse(app, wsgi_env);
		return r.toJWSGI();
	}
}
