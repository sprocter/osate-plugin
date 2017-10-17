package org.sireum.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

public class LaunchSireumHandler extends AbstractSireumHandler {
	@Override
	public Object execute(ExecutionEvent e) throws ExecutionException {

		String generator = e.getParameter("org.sireum.commands.launchsireum.generator");
		if (generator == null) {
			throw new RuntimeException("Unable to retrive generator argument");
		}

		this.setGenerator(generator);

		return super.execute(e);
	}
}
