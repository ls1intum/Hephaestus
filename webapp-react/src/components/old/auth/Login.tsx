import { useAuth } from '../../../lib/auth/AuthContext';
import { Button } from '../../ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../../ui/card';

export function Login() {
  const { login, isLoading } = useAuth();

  return (
    <div className="flex flex-col items-center justify-center min-h-[50vh] p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-center">Authentication Required</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <p className="text-center text-muted-foreground">
            You need to sign in to access this page.
          </p>
          
          <div className="flex justify-center">
            <Button 
              onClick={() => login()}
              disabled={isLoading}
              className="w-full max-w-xs"
            >
              {isLoading ? "Loading..." : "Sign In"}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}